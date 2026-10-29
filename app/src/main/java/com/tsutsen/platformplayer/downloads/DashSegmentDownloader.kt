package com.tsutsen.platformplayer.downloads

import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.IJSDashManifestRawSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.tsutsen.platformplayer.engine.exceptions.ScriptReloadRequiredException
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.others.Language
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** DASH SegmentTemplate strategy. */
class DashSegmentDownloader(
    private val owner: VideoDownload,
    private val ctx: SegmentDownloadContext,
    private val name: String,
    private val source: IJSDashManifestRawSource,
    private val downloadType: Int,
    private val audioTarget: File?,
) : SegmentDownloader {
    override suspend fun download(target: File, onProgress: (Long, Long, Long) -> Unit): Long =
        downloadDashFileSource(target, onProgress)

    private fun downloadDashFileSource(targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();
        if(audioTarget?.exists() ?: false)
            audioTarget.delete();

        targetFile.createNewFile();
        audioTarget?.createNewFile();

        val sourceLength: Long?;
        val sourceLengthAudio: Long?;
        val fileStream = FileOutputStream(targetFile);
        val fileStream2 = if(audioTarget != null) FileOutputStream(audioTarget) else null;

        var executor: JSRequestExecutor? = null;
        try{
            var manifest = source.manifest;
            if(source.hasGenerate)
                manifest = source.generate();
            if(manifest == null)
                throw IllegalStateException("No manifest after generation");

            //TODO: Temporary naive assume single-sourced dash
            val foundTemplates = VideoDownload.REGEX_DASH_TEMPLATE_WITH_MIME.findAll(manifest);
            val foundTemplate = when(downloadType) {
                1 -> foundTemplates.find({ it.groupValues[1].contains("video/") });
                2 -> foundTemplates.find({ it.groupValues[1].contains("audio/") });
                else -> foundTemplates.find({ it.groupValues[1].contains("video/") });
            }
            if(foundTemplate == null || foundTemplate.groupValues.size != 4)
                throw IllegalStateException("No SegmentTemplate found in manifest (unsupported dash?)");
            val foundTemplateUrl = foundTemplate.groupValues[2];
            val foundCues = VideoDownload.REGEX_DASH_CUE.findAll(foundTemplate.groupValues[3]).toList();
            if(foundCues.count() <= 0)
                throw IllegalStateException("No Cues found in manifest (unsupported dash?)");

            val foundTemplate2 = if(downloadType == 3) foundTemplates.find({ it.groupValues[1].contains("audio/") }); else null;
            val foundTemplateUrl2 = if(foundTemplate2 != null) foundTemplate2.groupValues[2] else null;
            val foundCues2 = if(foundTemplate2 != null) VideoDownload.REGEX_DASH_CUE.findAll(foundTemplate2.groupValues[3]).toList() else null;
            val foundCues2Downloaded = hashSetOf<MatchResult>();

            if(foundTemplate2 != null)
                owner.overrideResultAudioSource = LocalAudioSource((owner.videoSource?.name)?.let { it + " [audio]" } ?: "audio", "", 0, 0, foundTemplate2.groupValues[1], VideoDownload.REGEX_CODECS.find(foundTemplate2.groupValues[0])?.groupValues?.get(1) ?: "", Language.UNKNOWN);

            executor = if(source is JSSource && source.hasRequestExecutor)
                source.getRequestExecutor();
            else
                null;

            val modifier = if (source is JSSource && source.hasRequestModifier)
                source.getRequestModifier();
            else
                null;
            val speedTracker = VideoDownload.SpeedTracker(1000);

            Logger.i(VideoDownload.TAG, "Download $name Dash, CueCount: " + foundCues.count().toString());

            var written: Long = 0;
            var written2: Long = 0;
            var indexCounter = 0;
            var indexCounter2 = 0;
            onProgress(foundCues.count().toLong(), 0, 0);
            val totalCues = foundCues.count().toLong() + (foundCues2?.count()?.toLong() ?: 0)
            val lastCue = foundCues.lastOrNull();
            for(cue in foundCues) {
                val t = cue.groupValues[1];
                val d = cue.groupValues[2];

                Logger.i(VideoDownload.TAG, "Downloading cue ${indexCounter}")
                val url = foundTemplateUrl.replace("\$Number\$", (indexCounter).toString());
                val data = owner.executeOrGet(ctx.client, executor, modifier, url)
                fileStream.write(data, 0, data.size);
                speedTracker.addWork(data.size.toLong());
                written += data.size;

                onProgress(totalCues, indexCounter.toLong() + indexCounter2.toLong(), speedTracker.lastSpeed);


                indexCounter++;

                if(foundCues2 != null && foundTemplateUrl2 != null && fileStream2 != null) {
                    val toDownload = if(lastCue != null && cue == lastCue)
                        foundCues2.filter { !foundCues2Downloaded.contains(it) }.toList() else
                        foundCues2.filter { !foundCues2Downloaded.contains(it) && (it.groupValues[1].toLong()) < t.toLong() }.toList();
                    Logger.i(VideoDownload.TAG, "Downloading audio cues (${toDownload.size})")
                    for(cue2 in toDownload) {
                        val index2 = foundCues2.indexOf(cue2);
                        val t2 = cue2.groupValues[1];
                        val d2 = cue2.groupValues[2];
                        val url2 = foundTemplateUrl2!!.replace("\$Number\$", (index2).toString());
                        val data = owner.executeOrGet(ctx.client, executor, modifier, url2)
                        fileStream2.write(data, 0, data.size);
                        speedTracker.addWork(data.size.toLong());
                        written2 += data.size;
                        indexCounter2++;

                        foundCues2Downloaded.add(cue2);
                        onProgress(totalCues, indexCounter.toLong() + indexCounter2.toLong(), speedTracker.lastSpeed);
                    }
                }
            }

            try {
                val tailUrl = foundTemplateUrl.replace("\$Number\$", indexCounter.toString());
                Logger.i(VideoDownload.TAG, "Downloading tail segment (segIndex=$indexCounter)");
                val tailData = owner.executeOrGet(ctx.client, executor, modifier, tailUrl);
                fileStream.write(tailData, 0, tailData.size);
                speedTracker.addWork(tailData.size.toLong());
                written += tailData.size;
            } catch (ex: Throwable) {
                Logger.w(VideoDownload.TAG, "$name tail segment fetch (segIndex=$indexCounter) failed; continuing without it", ex);
            }

            if (foundCues2 != null && foundTemplateUrl2 != null && fileStream2 != null) {
                try {
                    val tailUrl2 = foundTemplateUrl2!!.replace("\$Number\$", foundCues2.size.toString());
                    Logger.i(VideoDownload.TAG, "Downloading audio tail segment (segIndex=${foundCues2.size})");
                    val tailData2 = owner.executeOrGet(ctx.client, executor, modifier, tailUrl2);
                    fileStream2.write(tailData2, 0, tailData2.size);
                    speedTracker.addWork(tailData2.size.toLong());
                    written2 += tailData2.size;
                } catch (ex: Throwable) {
                    Logger.w(VideoDownload.TAG, "$name(audio) tail segment fetch (segIndex=${foundCues2.size}) failed; continuing without it", ex);
                }
            }

            sourceLength = written;
            sourceLengthAudio = written2;

            Logger.i(VideoDownload.TAG, "$name downloadSource Finished");
        }
        catch(scriptEx: ScriptReloadRequiredException) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(audioTarget?.exists() ?: false)
                audioTarget.delete();

            owner.createNewPluginClient();
            throw scriptEx;
        }
        catch(ioex: IOException) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(audioTarget?.exists() ?: false)
                audioTarget.delete();
            if(ioex.message?.contains("ENOSPC") ?: false)
                throw Exception("Not enough space on device", ioex);
            else
                throw ioex;
        }
        catch(ex: Throwable) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(audioTarget?.exists() ?: false)
                audioTarget.delete();
            throw ex;
        }
        finally {
            fileStream.close();
            fileStream2?.close();
            executor?.closeAsync()
        }
        if(sourceLengthAudio != null && sourceLengthAudio > 0)
            owner.audioFileSize = sourceLengthAudio
        return sourceLength!!;
    }
}
