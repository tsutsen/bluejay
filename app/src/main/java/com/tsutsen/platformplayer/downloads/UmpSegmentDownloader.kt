package com.tsutsen.platformplayer.downloads

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUMPAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUMPSource
import com.tsutsen.platformplayer.exceptions.DownloadException
import com.tsutsen.platformplayer.others.Language
import com.tsutsen.platformplayer.sabr.SabrFormat
import com.tsutsen.platformplayer.sabr.SabrSession
import com.tsutsen.platformplayer.sabr.SabrStreamSpec
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** UMP/SABR strategy: downloads the selected video and/or audio tracks. */
class UmpSegmentDownloader(
    private val owner: VideoDownload,
    private val ctx: SegmentDownloadContext,
    private val source: JSUMPSource? = null,
    private val audioSource: JSUMPAudioSource? = null,
    private val audioFile: File? = null,
) : SegmentDownloader {
    /** Bytes written for the audio track, when this download carried one. */
    var audioSize: Long = 0
        private set

    override suspend fun download(target: File, onProgress: (Long, Long, Long) -> Unit): Long {
        audioSource?.let { return downloadUMPAudioOnly(ctx.client, it, target, onProgress) }
        val (videoLength, audioLength) = downloadUMPSource(ctx.client, source!!, target, audioFile, onProgress)
        audioSize = audioLength
        return videoLength
    }

    private fun downloadUMPSource(client: ManagedHttpClient, source: JSUMPSource, videoFile: File, audioFile: File?, onProgress: (Long, Long, Long) -> Unit): Pair<Long, Long> {
        if(source.isLive)
            throw DownloadException("Live streams cannot be downloaded", false);
        val video = selectBestUMPVideoFormat(source, owner.targetPixelCount);
        val audio = selectBestUMPAudioFormat(source);
        if(video == null && audio == null)
            throw DownloadException("UMP source has no downloadable formats", false);

        val durationSec = source.duration.coerceAtLeast(0);
        val videoEstimate = if(video != null) video.bitrate.toLong() / 8 * durationSec else 0L;
        val audioEstimate = if(audio != null) audio.bitrate.toLong() / 8 * durationSec else 0L;

        if(video != null)
            owner.videoOverrideContainer = video.containerMimeType;

        if(audio != null && audioFile != null) {
            owner.overrideResultAudioSource = LocalAudioSource(
                (source.name) + " [audio]", "", 0, audio.bitrate,
                audio.containerMimeType, audio.codecs, audio.language ?: Language.UNKNOWN);
            owner.audioOverrideContainer = audio.containerMimeType;
        }

        val spec = source.toStreamSpec({ client });
        val concurrency = umpDownloadConcurrency();
        val progress = UMPProgress(onProgress);
        if(video != null) progress.seed(SabrSession.ROLE_VIDEO, videoEstimate);
        if(audio != null && audioFile != null) progress.seed(SabrSession.ROLE_AUDIO, audioEstimate);

        var videoLength = 0L;
        var audioLength = 0L;
        if(video != null) {
            videoLength = downloadUMPTrackParallel(spec, SabrSession.ROLE_VIDEO, video, durationSec, videoFile, concurrency, videoEstimate) { read, estimate ->
                progress.report(SabrSession.ROLE_VIDEO, read, estimate);
            };
            progress.complete(SabrSession.ROLE_VIDEO, videoLength);
        }
        if(audio != null && audioFile != null) {
            audioLength = downloadUMPTrackParallel(spec, SabrSession.ROLE_AUDIO, audio, durationSec, audioFile, concurrency, audioEstimate) { read, estimate ->
                progress.report(SabrSession.ROLE_AUDIO, read, estimate);
            };
            progress.complete(SabrSession.ROLE_AUDIO, audioLength);
        }
        return Pair(videoLength, audioLength);
    }

    private fun downloadUMPAudioOnly(client: ManagedHttpClient, source: JSUMPAudioSource, audioFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        val parent = source.parent;
        if(parent.isLive)
            throw DownloadException("Live streams cannot be downloaded", false);
        val format = source.format;

        owner.audioOverrideContainer = format.containerMimeType;
        val durationSec = parent.duration.coerceAtLeast(0);
        val estimate = format.bitrate.toLong() / 8 * durationSec;

        val spec = parent.toStreamSpec({ client });
        val progress = UMPProgress(onProgress);
        progress.seed(SabrSession.ROLE_AUDIO, estimate);
        val length = downloadUMPTrackParallel(spec, SabrSession.ROLE_AUDIO, format, durationSec, audioFile, umpDownloadConcurrency(), estimate) { read, est ->
            progress.report(SabrSession.ROLE_AUDIO, read, est);
        };
        progress.complete(SabrSession.ROLE_AUDIO, length);
        return length;
    }

    private class UMPProgress(private val onProgress: (Long, Long, Long) -> Unit) {
        private val lock = Object();
        private val read = HashMap<Int, Long>();
        private val totals = HashMap<Int, Long>();
        private var windowStartMs = 0L;
        private var windowStartBytes = 0L;
        private var speed = 0L;
        private var lastEmitMs = 0L;

        fun seed(role: Int, estimate: Long) {
            synchronized(lock) {
                read[role] = 0;
                totals[role] = estimate;
            }
        }

        fun report(role: Int, trackRead: Long, trackTotal: Long) = emit(role, trackRead, trackTotal, false);
        fun complete(role: Int, trackLength: Long) = emit(role, trackLength, trackLength, true);

        private fun emit(role: Int, trackRead: Long, trackTotal: Long, force: Boolean) {
            synchronized(lock) {
                read[role] = trackRead;
                totals[role] = maxOf(trackTotal, trackRead);

                val totalRead = read.values.sum();
                val totalLength = totals.values.sum();
                val now = System.currentTimeMillis();

                if(windowStartMs == 0L) {
                    windowStartMs = now;
                    windowStartBytes = totalRead;
                }
                else if(now - windowStartMs >= SPEED_WINDOW_MS) {
                    speed = (totalRead - windowStartBytes) * 1000L / (now - windowStartMs);
                    windowStartMs = now;
                    windowStartBytes = totalRead;
                }

                if(force || now - lastEmitMs >= 200) {
                    lastEmitMs = now;
                    onProgress(maxOf(totalLength, totalRead), totalRead, speed);
                }
            }
        }

        companion object {
            private const val SPEED_WINDOW_MS = 1000L;
        }
    }

    private fun downloadUMPTrack(session: SabrSession, role: Int, format: SabrFormat, targetFile: File, fallbackEstimate: Long, onRead: (Long, Long) -> Unit): Long {
        if(targetFile.exists()) targetFile.delete();
        targetFile.createNewFile();

        val buffer = session.bufferFor(format);
        var initSize = 0L;
        var segmentBytes = 0L;
        var segmentCount = 0;

        val report = {
            val written = initSize + segmentBytes;
            val endSegment = session.formatInitializationFor(format)?.endSegmentNumber ?: 0;
            val estimate = if(endSegment > 0 && segmentCount > 0) initSize + segmentBytes * endSegment / segmentCount
                else fallbackEstimate;
            onRead(written, maxOf(estimate, written));
        }

        FileOutputStream(targetFile).use { out ->
            session.setPlaybackPosition(0);
            session.setDemand(role, format, 0);

            var init = awaitUMPSegment(session, buffer, isInit = true, sequence = -1);
            val initDeadline = System.currentTimeMillis() + UMP_DOWNLOAD_MAX_STALL_MS;
            while(init == null && System.currentTimeMillis() < initDeadline) {
                if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                session.fatalError?.let { throw it };
                init = awaitUMPSegment(session, buffer, isInit = true, sequence = -1);
            }
            if(init == null)
                throw DownloadException("UMP init segment for itag ${format.itag} never arrived", false);
            val initBytes = init.toByteArray();
            out.write(initBytes);
            initSize = initBytes.size.toLong();
            report();

            var nextSeq = -1;
            var lastProgressMs = System.currentTimeMillis();
            while(true) {
                if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                session.fatalError?.let { throw it };

                val endSeg = session.formatInitializationFor(format)?.endSegmentNumber ?: 0;
                if(endSeg > 0 && nextSeq > endSeg) break;

                val segment = if(nextSeq < 0) awaitUMPSegmentAt(session, buffer, 0)
                    else awaitUMPSegment(session, buffer, isInit = false, sequence = nextSeq);
                if(segment == null) {
                    if(session.isComplete(format)) break;
                    if(System.currentTimeMillis() - lastProgressMs > UMP_DOWNLOAD_MAX_STALL_MS)
                        throw DownloadException("UMP download stalled for itag ${format.itag} before reaching the end", false);
                    continue;
                }
                val bytes = segment.toByteArray();
                out.write(bytes);
                segmentBytes += bytes.size;
                segmentCount++;
                lastProgressMs = System.currentTimeMillis();
                report();

                nextSeq = segment.sequenceNumber + 1;
                session.setPlaybackPosition(segment.endUs);
                session.setDemand(role, format, segment.endUs);
            }
        }
        return initSize + segmentBytes;
    }

    private fun awaitUMPSegment(session: SabrSession, buffer: com.tsutsen.platformplayer.sabr.SabrTrackBuffer, isInit: Boolean, sequence: Int): com.tsutsen.platformplayer.sabr.SabrSegment? {
        val deadline = System.currentTimeMillis() + UMP_DOWNLOAD_POLL_MS;
        while(System.currentTimeMillis() < deadline) {
            if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
            session.fatalError?.let { throw it };

            val segment = if(isInit) buffer.initSegment else buffer.get(sequence);
            if(segment != null) {
                while(!segment.isComplete) {
                    if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                    session.fatalError?.let { throw it };
                    if(System.currentTimeMillis() >= deadline) return null;
                    session.wakePump();
                    buffer.awaitBytes(segment, segment.size, 2000);
                }
                return segment;
            }

            session.wakePump();
            buffer.awaitSequence(sequence, 2000);
        }
        return null;
    }

    private fun umpDownloadConcurrency(): Int {
        val plugin = (owner.video?.url ?: owner.videoDetails?.url)?.let { StatePlatform.instance.getContentClient(it) }?.let { if(it is JSClient) it else null };
        val maxParallel = if(plugin != null && plugin.config.maxDownloadParallelism > 0) plugin.config.maxDownloadParallelism else 99;
        return Math.min(maxParallel, Settings.instance.downloads.getByteRangeThreadCount()).coerceIn(1, 6);
    }

    private fun awaitUMPSegmentAt(session: SabrSession, buffer: com.tsutsen.platformplayer.sabr.SabrTrackBuffer, positionUs: Long): com.tsutsen.platformplayer.sabr.SabrSegment? {
        val deadline = System.currentTimeMillis() + UMP_DOWNLOAD_POLL_MS;
        while(System.currentTimeMillis() < deadline) {
            if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
            session.fatalError?.let { throw it };

            val segment = buffer.firstCovering(positionUs);
            if(segment != null) {
                while(!segment.isComplete) {
                    if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                    session.fatalError?.let { throw it };
                    if(System.currentTimeMillis() >= deadline) return null;
                    session.wakePump();
                    buffer.awaitBytes(segment, segment.size, 2000);
                }
                return segment;
            }

            session.wakePump();
            buffer.awaitCovering(positionUs, 2000);
        }
        return null;
    }

    private fun downloadUMPTrackParallel(spec: SabrStreamSpec, role: Int, format: SabrFormat, durationSec: Long, targetFile: File, concurrency: Int, fallbackEstimate: Long, onRead: (Long, Long) -> Unit): Long {
        val maxBySlice = if(durationSec > 0) (durationSec / UMP_PARALLEL_MIN_SLICE_SEC).toInt() else 1;
        val n = concurrency.coerceIn(1, 6).coerceAtMost(maxBySlice.coerceAtLeast(1));
        val totalUs = durationSec * 1_000_000L;
        if(n <= 1 || totalUs <= 0L) {
            val session = spec.createSession().apply { keepBehindUs = UMP_DOWNLOAD_KEEP_BEHIND_US };
            session.start();
            try { return downloadUMPTrack(session, role, format, targetFile, fallbackEstimate, onRead); }
            finally { session.release(); }
        }

        if(targetFile.exists()) targetFile.delete();
        val tmpDir = File(targetFile.parentFile, targetFile.name + ".umpparts");
        if(tmpDir.exists()) tmpDir.deleteRecursively();
        tmpDir.mkdirs();

        val claimed = ConcurrentHashMap.newKeySet<Int>();
        val initSize = AtomicLong(0);
        val segmentBytes = AtomicLong(0);
        val endSegment = AtomicInteger(0);
        val initHolder = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null);

        val report = {
            val init = initSize.get();
            val segments = segmentBytes.get();
            val count = claimed.size;
            val end = endSegment.get();
            val written = init + segments;
            val estimate = if(end > 0 && count > 0) init + segments * end / count else fallbackEstimate;
            onRead(written, maxOf(estimate, written));
        }

        val pool = Executors.newFixedThreadPool(n);
        try {
            val futures = (0 until n).map { i ->
                pool.submit {
                    val startUs = i * totalUs / n;
                    val endUs = if(i == n - 1) Long.MAX_VALUE else (i + 1) * totalUs / n;
                    val session = spec.createSession().apply { keepBehindUs = UMP_DOWNLOAD_KEEP_BEHIND_US };
                    val buffer = session.bufferFor(format);
                    try {
                        session.start();
                        session.setPlaybackPosition(startUs);
                        session.setDemand(role, format, startUs);
                        session.restart(startUs);

                        if(i == 0) {
                            var init = awaitUMPSegment(session, buffer, isInit = true, sequence = -1);
                            val initDeadline = System.currentTimeMillis() + UMP_DOWNLOAD_MAX_STALL_MS;
                            while(init == null && System.currentTimeMillis() < initDeadline) {
                                if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                                session.fatalError?.let { throw it };
                                init = awaitUMPSegment(session, buffer, isInit = true, sequence = -1);
                            }
                            if(init == null) throw DownloadException("UMP init segment for itag ${format.itag} never arrived", false);
                            val b = init.toByteArray();
                            initHolder.set(b);
                            initSize.set(b.size.toLong());
                            report();
                        }

                        FileOutputStream(File(tmpDir, "part_$i")).use { out ->
                            var nextSeq = -1;
                            var lastProgressMs = System.currentTimeMillis();
                            while(true) {
                                if(ctx.isCancelled()) throw CancellationException("Download got cancelled");
                                session.fatalError?.let { throw it };

                                val segment = if(nextSeq < 0) awaitUMPSegmentAt(session, buffer, startUs)
                                    else awaitUMPSegment(session, buffer, isInit = false, sequence = nextSeq);
                                if(segment == null) {
                                    if(session.isComplete(format)) break;
                                    if(System.currentTimeMillis() - lastProgressMs > UMP_DOWNLOAD_MAX_STALL_MS)
                                        throw DownloadException("UMP download stalled for itag ${format.itag} before reaching the end", false);
                                    continue;
                                }
                                if(segment.startUs >= endUs) break;
                                nextSeq = segment.sequenceNumber + 1;
                                session.setPlaybackPosition(segment.endUs);
                                session.setDemand(role, format, segment.endUs);
                                lastProgressMs = System.currentTimeMillis();
                                session.formatInitializationFor(format)?.endSegmentNumber?.let {
                                    if(it > 0) endSegment.set(it);
                                }
                                if(claimed.add(segment.sequenceNumber)) {
                                    val bytes = segment.toByteArray();
                                    out.write(bytes);
                                    segmentBytes.addAndGet(bytes.size.toLong());
                                    report();
                                }
                                val lastSeg = endSegment.get();
                                if(lastSeg > 0 && nextSeq > lastSeg) break;
                            }
                        }
                    }
                    finally { session.release(); }
                }
            };
            var firstError: Throwable? = null;
            for(future in futures) {
                try { future.get(); }
                catch(ex: ExecutionException) { if(firstError == null) firstError = ex.cause ?: ex; }
                catch(ex: Throwable) { if(firstError == null) firstError = ex; }
            }
            firstError?.let { tmpDir.deleteRecursively(); throw it; }
        }
        finally { pool.shutdownNow(); }

        var total = 0L;
        FileOutputStream(targetFile).use { out ->
            initHolder.get()?.let { out.write(it); total += it.size; }
            for(i in 0 until n) {
                val part = File(tmpDir, "part_$i");
                if(part.exists()) FileInputStream(part).use { total += it.copyTo(out); }
            }
        }
        tmpDir.deleteRecursively();
        onRead(total, total);
        return total;
    }

}

private const val UMP_PARALLEL_MIN_SLICE_SEC = 20L
private const val UMP_DOWNLOAD_POLL_MS = 15_000L
private const val UMP_DOWNLOAD_KEEP_BEHIND_US = 2_000_000L
private const val UMP_DOWNLOAD_MAX_STALL_MS = 120_000L

internal fun selectBestUMPVideoFormat(source: JSUMPSource, targetPixelCount: Long?): SabrFormat? {
    if (source.videoFormats.isEmpty()) return null
    val target = targetPixelCount
    return if (target != null && target > 0) {
        source.videoFormats.minByOrNull { kotlin.math.abs(it.width.toLong() * it.height - target) }
    } else {
        source.videoFormats.maxByOrNull { it.height.toLong() * 100000 + it.bitrate }
    }
}

internal fun selectBestUMPAudioFormat(source: JSUMPSource): SabrFormat? {
    if (source.audioFormats.isEmpty()) return null
    val original = source.audioFormats.filter { it.isOriginalAudio }
    val pool = if (original.isNotEmpty()) original else source.audioFormats
    return pool.maxByOrNull { it.bitrate }
}
