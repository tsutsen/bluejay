package com.tsutsen.platformplayer.downloads

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.toHumanBytesSpeed
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ThreadLocalRandom

/** Progressive/byte-range file strategy. */
class ProgressiveSegmentDownloader(
    private val owner: VideoDownload,
    private val ctx: SegmentDownloadContext,
    private val name: String,
    private val url: String,
) : SegmentDownloader {
    override suspend fun download(target: File, onProgress: (Long, Long, Long) -> Unit): Long =
        downloadFileSource(target, onProgress)

    private fun downloadFileSource(targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        targetFile.createNewFile();

        val sourceLength: Long?;
        val fileStream = FileOutputStream(targetFile);

        try {
            val head = ctx.client.tryHead(url, ctx.modifier);
            val relatedPlugin = (owner.video?.url ?: owner.videoDetails?.url)?.let { StatePlatform.instance.getContentClient(it) }?.let { if(it is JSClient) it else null };
            if(Settings.instance.downloads.byteRangeDownload && head?.containsKey("accept-ranges") == true && head.containsKey("content-length"))
            {
                val maxParallel = if(relatedPlugin != null && relatedPlugin.config.maxDownloadParallelism > 0)
                    relatedPlugin.config.maxDownloadParallelism else 99;
                val concurrency = Math.min(maxParallel, Settings.instance.downloads.getByteRangeThreadCount());
                Logger.i(VideoDownload.TAG, "Download $name ByteRange Parallel (${concurrency}): " + url);
                sourceLength = head["content-length"]!!.toLong();
                onProgress(sourceLength, 0, 0);
                downloadSource_Ranges(name, ctx.client, ctx.modifier, fileStream, url, sourceLength, 1024*512, concurrency, onProgress);
            }
            else {
                Logger.i(VideoDownload.TAG, "Download $name Sequential");
                try {
                    sourceLength = downloadSource_Sequential(ctx.client, ctx.modifier, fileStream, url, null, 0, onProgress);
                } catch (e: Throwable) {
                    Logger.w(VideoDownload.TAG, "Failed to download sequentially (url = $url)")
                    throw e
                }
            }

            Logger.i(VideoDownload.TAG, "$name downloadSource Finished");
        }
        catch(ioex: IOException) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(ioex.message?.contains("ENOSPC") ?: false)
                throw Exception("Not enough space on device", ioex);
            else
                throw ioex;
        }
        catch(ex: Throwable) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            throw ex;
        }
        finally {
            fileStream.close();
        }
        return sourceLength!!;
    }

    private fun downloadSource_Sequential(client: ManagedHttpClient, modifier: IRequestModifier? = null, fileStream: FileOutputStream, url: String, decryptionInfo: VideoDownload.DecryptionInfo?, index: Int, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 5;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 5;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        val result = client.get(url, HashMap(), modifier)
        if (!result.isOk) {
            result.body?.close()
            throw IllegalStateException("Failed to download source. Web[${result.code}] Error");
        }
        if (result.body == null)
            throw IllegalStateException("Failed to download source. Web[${result.code}] No response");

        val sourceLength = result.body.contentLength();
        val sourceStream = result.body.byteStream();

        // Only encrypted bodies need buffering; plain bodies are streamed
        // straight to the target file.
        val segmentBuffer = if (decryptionInfo != null) ByteArrayOutputStream() else null

        var totalRead: Long = 0;
        try {
            var read: Int;
            val buffer = ByteArray(4096);

            do {
                read = sourceStream.read(buffer);
                if (read < 0)
                    break;

                if (segmentBuffer != null) segmentBuffer.write(buffer, 0, read)
                else fileStream.write(buffer, 0, read)

                totalRead += read;

                readSinceLastSpeedTest += read;
                if (totalRead.toDouble() / progressRate > lastProgressCount) {
                    onProgress(sourceLength, totalRead, lastSpeed);
                    lastProgressCount++;
                }
                if (readSinceLastSpeedTest > speedRate) {
                    val lastSpeedTime = timeSinceLastSpeedTest;
                    timeSinceLastSpeedTest = System.currentTimeMillis();
                    val timeSince = timeSinceLastSpeedTest - lastSpeedTime;
                    if (timeSince > 0)
                        lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong();
                    readSinceLastSpeedTest = 0;
                }

                if (ctx.isCancelled())
                    throw CancellationException("Cancelled");
            } while (read > 0);
        } finally {
            sourceStream.close()
            result.body.close()
        }

        if (decryptionInfo != null) {
            var iv = decryptionInfo.iv
            if (iv == null) {
                iv = ByteBuffer.allocate(16)
                    .putLong(0L)
                    .putLong(index.toLong())
                    .array()
            }

            val decryptedData = decryptSegment(segmentBuffer!!.toByteArray(), decryptionInfo.key, iv!!)
            fileStream.write(decryptedData)
        }

        onProgress(sourceLength, totalRead, 0);
        return sourceLength;
    }

    private fun downloadSource_Ranges(name: String, client: ManagedHttpClient, modifier: IRequestModifier?, fileStream: FileOutputStream, url: String, sourceLength: Long, rangeSize: Int, concurrency: Int = 1, onProgress: (Long, Long, Long) -> Unit) {
        val progressRate: Int = 4096 * 5;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 5;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        var reqCount = -1;
        var totalRead: Long = 0;

        val pool = ForkJoinPool(concurrency);

        try {
            while(totalRead < sourceLength) {
                reqCount++;

                Logger.i(VideoDownload.TAG, "Download ${name} Batch #${reqCount} [${concurrency}] (${lastSpeed.toHumanBytesSpeed()})");

                val byteRangeResults = requestByteRangeParallel(client, pool, modifier, url, sourceLength, concurrency, totalRead,
                    rangeSize, 1024 * 64);

                for(byteRange in byteRangeResults) {
                    val read = ((byteRange.third - byteRange.second) + 1).toInt();

                    fileStream.write(byteRange.first, 0, read);

                    totalRead += read;
                    readSinceLastSpeedTest += read;
                }

                if(readSinceLastSpeedTest > speedRate) {
                    val lastSpeedTime = timeSinceLastSpeedTest;
                    timeSinceLastSpeedTest = System.currentTimeMillis();
                    val timeSince = timeSinceLastSpeedTest - lastSpeedTime;
                    if(timeSince > 0)
                        lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong();
                    readSinceLastSpeedTest = 0;
                }
                if(totalRead / progressRate > lastProgressCount) {
                    onProgress(sourceLength, totalRead, lastSpeed);
                    lastProgressCount++;
                }

                if(ctx.isCancelled())
                    throw CancellationException("Cancelled", null);
            }
        } finally {
            pool.shutdownNow();
        }
        onProgress(sourceLength, totalRead, 0);
    }

    private fun requestByteRangeParallel(client: ManagedHttpClient, pool: ForkJoinPool, modifier: IRequestModifier?, url: String, totalLength: Long, concurrency: Int, rangePosition: Long, rangeSize: Int, rangeVariance: Int = -1): List<Triple<ByteArray, Long, Long>> {
        val tasks = mutableListOf<ForkJoinTask<Triple<ByteArray, Long, Long>>>();
        var readPosition = rangePosition;
        for(i in 0 until concurrency) {
            if(readPosition >= totalLength - 1)
                continue;

            val toRead = rangeSize + (if(rangeVariance >= 1) ThreadLocalRandom.current().nextInt(rangeVariance * -1, rangeVariance) else 0);
            val rangeStart = readPosition;
            val rangeEnd = if(rangeStart + toRead > totalLength)
                totalLength - 1;
            else readPosition + toRead;

            tasks.add(pool.submit<Triple<ByteArray, Long, Long>> {
                return@submit requestByteRange(client, modifier, url, rangeStart, rangeEnd);
            });
            readPosition = rangeEnd + 1;
        }

        return tasks.map { it.get() };
    }
    private fun requestByteRange(client: ManagedHttpClient, modifier: IRequestModifier?, url: String, rangeStart: Long, rangeEnd: Long): Triple<ByteArray, Long, Long> {
        var retryCount = 0
        var lastException: Throwable? = null;

        val headers = mutableMapOf(Pair("Range", "bytes=${rangeStart}-${rangeEnd}"));

        while (retryCount <= 3) {
            try {
                val toRead = rangeEnd - rangeStart;

                val req = client.get(url, headers.toMutableMap(), modifier);
                if (!req.isOk) {
                    val bodyString = req.body?.string()
                    req.body?.close()
                    throw IllegalStateException("Range request failed Code [${req.code}] due to: ${req.message}");
                }
                if (req.body == null)
                    throw IllegalStateException("Range request failed, No body");
                val read = req.body.contentLength();

                if (read < toRead)
                    throw IllegalStateException("Byte-Range request attempted to provide less (${read} < ${toRead})");

                return Triple(req.body.bytes(), rangeStart, rangeEnd);
            } catch (e: Throwable) {
                Logger.w(VideoDownload.TAG, "Failed to download range (url=${url} bytes=${rangeStart}-${rangeEnd})", e)

                retryCount++
                lastException = e

                sleep(when (retryCount) {
                    1 -> 1000 + ((Math.random() * 300.0).toLong() - 150)
                    2 -> 2000 + ((Math.random() * 300.0).toLong() - 150)
                    3 -> 4000 + ((Math.random() * 300.0).toLong() - 150)
                    else -> 1000 + ((Math.random() * 300.0).toLong() - 150)
                })
            }
        }

        throw lastException!!
    }
}
