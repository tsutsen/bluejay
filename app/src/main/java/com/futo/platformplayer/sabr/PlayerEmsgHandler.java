package com.futo.platformplayer.sabr;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.common.ParserException;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.common.Metadata;
import androidx.media3.extractor.metadata.MetadataInputBuffer;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.emsg.EventMessageDecoder;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.chunk.Chunk;
import com.futo.platformplayer.sabr.manifest.SabrManifest;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

@UnstableApi
public final class PlayerEmsgHandler implements Handler.Callback {
    /** Callbacks for player emsg events encountered during DASH live stream. */
    public interface PlayerEmsgCallback {

        /** Called when the current manifest should be refreshed. */
        void onDashManifestRefreshRequested();

        /**
         * Called when the manifest with the publish time has been expired.
         *
         * @param expiredManifestPublishTimeUs The manifest publish time that has been expired.
         */
        void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs);
    }

    private final Allocator allocator;
    private final PlayerEmsgCallback playerEmsgCallback;
    private final EventMessageDecoder decoder;
    private SabrManifest manifest;
    private final Handler handler;
    private final TreeMap<Long, Long> manifestPublishTimeToExpiryTimeUs;

    private long expiredManifestPublishTimeUs;
    private long lastLoadedChunkEndTimeUs;
    private long lastLoadedChunkEndTimeBeforeRefreshUs;
    private boolean isWaitingForManifestRefresh;
    private boolean released;

    /**
     * @param manifest The initial manifest.
     * @param playerEmsgCallback The callback that this event handler can invoke when handling emsg
     *     messages that generate DASH media source events.
     * @param allocator An {@link Allocator} from which allocations can be obtained.
     */
    public PlayerEmsgHandler(
            SabrManifest manifest, PlayerEmsgCallback playerEmsgCallback, Allocator allocator) {
        this.manifest = manifest;
        this.playerEmsgCallback = playerEmsgCallback;
        this.allocator = allocator;

        manifestPublishTimeToExpiryTimeUs = new TreeMap<>();
        handler = Util.createHandlerForCurrentLooper(/* callback= */ this);
        decoder = new EventMessageDecoder();
    }

    /**
     * Updates the {@link SabrManifest} that this handler works on.
     *
     * @param newManifest The updated manifest.
     */
    public void updateManifest(SabrManifest newManifest) {
        isWaitingForManifestRefresh = false;
        expiredManifestPublishTimeUs = C.TIME_UNSET;
        this.manifest = newManifest;
        removePreviouslyExpiredManifestPublishTimeValues();
    }

    /* package */ boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
        if (!manifest.dynamic) {
            return false;
        }
        if (isWaitingForManifestRefresh) {
            return true;
        }
        boolean manifestRefreshNeeded = false;
        // Find the smallest publishTime (greater than or equal to the current manifest's publish time)
        // that has a corresponding expiry time.
        Map.Entry<Long, Long> expiredEntry = ceilingExpiryEntryForPublishTime(manifest.publishTimeMs);
        if (expiredEntry != null) {
            long expiredPointUs = expiredEntry.getValue();
            if (expiredPointUs < presentationPositionUs) {
                expiredManifestPublishTimeUs = expiredEntry.getKey();
                notifyManifestPublishTimeExpired();
                manifestRefreshNeeded = true;
            }
        }
        if (manifestRefreshNeeded) {
            maybeNotifyDashManifestRefreshNeeded();
        }
        return manifestRefreshNeeded;
    }

    /**
     * For live streaming with emsg event stream, forward seeking can seek pass the emsg messages that
     * signals end-of-stream or Manifest expiry, which results in load error. In this case, we should
     * notify the Dash media source to refresh its manifest.
     *
     * @param chunk The chunk whose load encountered the error.
     * @return True if manifest refresh has been requested, false otherwise.
     */
    /* package */ boolean maybeRefreshManifestOnLoadingError(Chunk chunk) {
        if (!manifest.dynamic) {
            return false;
        }
        if (isWaitingForManifestRefresh) {
            return true;
        }
        boolean isAfterForwardSeek =
                lastLoadedChunkEndTimeUs != C.TIME_UNSET && lastLoadedChunkEndTimeUs < chunk.startTimeUs;
        if (isAfterForwardSeek) {
            // if we are after a forward seek, and the playback is dynamic with embedded emsg stream,
            // there's a chance that we have seek over the emsg messages, in which case we should ask
            // media source for a refresh.
            maybeNotifyDashManifestRefreshNeeded();
            return true;
        }
        return false;
    }

    /**
     * Called when the a new chunk in the current media stream has been loaded.
     *
     * @param chunk The chunk whose load has been completed.
     */
    /* package */ void onChunkLoadCompleted(Chunk chunk) {
        if (lastLoadedChunkEndTimeUs == C.TIME_UNSET || chunk.endTimeUs > lastLoadedChunkEndTimeUs) {
            lastLoadedChunkEndTimeUs = chunk.endTimeUs;
        }
    }

    private @Nullable Map.Entry<Long, Long> ceilingExpiryEntryForPublishTime(long publishTimeMs) {
        return manifestPublishTimeToExpiryTimeUs.ceilingEntry(publishTimeMs);
    }

    private void removePreviouslyExpiredManifestPublishTimeValues() {
        for (Iterator<Entry<Long, Long>> it =
             manifestPublishTimeToExpiryTimeUs.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            long expiredManifestPublishTime = entry.getKey();
            if (expiredManifestPublishTime < manifest.publishTimeMs) {
                it.remove();
            }
        }
    }

    private void notifyManifestPublishTimeExpired() {
        playerEmsgCallback.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
    }

    /** Requests DASH media manifest to be refreshed if necessary. */
    private void maybeNotifyDashManifestRefreshNeeded() {
        if (lastLoadedChunkEndTimeBeforeRefreshUs != C.TIME_UNSET
                && lastLoadedChunkEndTimeBeforeRefreshUs == lastLoadedChunkEndTimeUs) {
            // Already requested manifest refresh.
            return;
        }
        isWaitingForManifestRefresh = true;
        lastLoadedChunkEndTimeBeforeRefreshUs = lastLoadedChunkEndTimeUs;
        playerEmsgCallback.onDashManifestRefreshRequested();
    }

    /** Returns a {@link TrackOutput} that emsg messages could be written to. */
    public PlayerTrackEmsgHandler newPlayerTrackEmsgHandler() {
        return new PlayerTrackEmsgHandler(SampleQueue.createWithoutDrm(allocator));
    }

    /** Release this emsg handler. It should not be reused after this call. */
    public void release() {
        released = true;
    }

    @Override
    public boolean handleMessage(Message message) {
        if (released) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether an event with given schemeIdUri and value is a DASH emsg event targeting the
     * player.
     */
    public static boolean isPlayerEmsgEvent(String schemeIdUri, String value) {
        return "urn:mpeg:sabr:event:2025".equals(schemeIdUri)
                && ("1".equals(value) || "2".equals(value) || "3".equals(value));
    }

    /** Handles emsg messages for a specific track for the player. */
    public final class PlayerTrackEmsgHandler implements TrackOutput {
        private final SampleQueue sampleQueue;
        private final FormatHolder formatHolder;
        private final MetadataInputBuffer buffer;

        private long maxLoadedChunkEndTimeUs;

        public PlayerTrackEmsgHandler(SampleQueue sampleQueue) {
            this.sampleQueue = sampleQueue;
            this.formatHolder = new FormatHolder();
            this.buffer = new MetadataInputBuffer();
            this.maxLoadedChunkEndTimeUs = C.TIME_UNSET;
        }

        @Override
        public void format(Format format) {
            sampleQueue.format(format);
        }

        @Override
        public int sampleData(
                DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
                throws IOException {
            return sampleQueue.sampleData(input, length, allowEndOfInput);
        }

        @Override
        public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
            sampleQueue.sampleData(data, length);
        }

        @Override
        public void sampleMetadata(
                long timeUs, int flags, int size, int offset, @Nullable CryptoData encryptionData) {
            sampleQueue.sampleMetadata(timeUs, flags, size, offset, encryptionData);
            parseAndDiscardSamples();
        }

        /** For live streaming: check expiry before loading the next chunk. */
        public boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
            return PlayerEmsgHandler.this.maybeRefreshManifestBeforeLoadingNextChunk(presentationPositionUs);
        }

        /** Called when a new chunk finished loading. */
        public void onChunkLoadCompleted(Chunk chunk) {
            if (maxLoadedChunkEndTimeUs == C.TIME_UNSET || chunk.endTimeUs > maxLoadedChunkEndTimeUs) {
                maxLoadedChunkEndTimeUs = chunk.endTimeUs;
            }
            PlayerEmsgHandler.this.onChunkLoadCompleted(chunk);
        }

        /** Called when a chunk load errored; may trigger a manifest refresh. */
        public boolean maybeRefreshManifestOnLoadingError(Chunk chunk) {
            return PlayerEmsgHandler.this.maybeRefreshManifestOnLoadingError(chunk);
        }

        /** Release this track emsg handler. It should not be reused after this call. */
        public void release() {
            sampleQueue.release();
        }

        private void parseAndDiscardSamples() {
            while (sampleQueue.isReady(/* loadingFinished= */ false)) {
                MetadataInputBuffer inputBuffer = dequeueSample();
                if (inputBuffer == null) {
                    continue;
                }
                long eventTimeUs = inputBuffer.timeUs;
                Metadata metadata = decoder.decode(inputBuffer);
                if (metadata == null) {
                    continue;
                }
                EventMessage eventMessage = (EventMessage) metadata.get(0);
                if (isPlayerEmsgEvent(eventMessage.schemeIdUri, eventMessage.value)) {
                    parsePlayerEmsgEvent(eventTimeUs, eventMessage);
                }
            }
            sampleQueue.discardToRead();
        }

        private void parsePlayerEmsgEvent(long eventTimeUs, EventMessage eventMessage) {
            // NOP
        }

        @Nullable
        private MetadataInputBuffer dequeueSample() {
            buffer.clear();
            int result = sampleQueue.read(
                    formatHolder, buffer, /* readFlags= */ 0, /* loadingFinished= */ false);
            if (result == C.RESULT_BUFFER_READ) {
                buffer.flip();
                return buffer;
            }
            return null;
        }
    }

    /** Holds information related to a manifest expiry event. */
    private static final class ManifestExpiryEventInfo {

        public final long eventTimeUs;
        public final long manifestPublishTimeMsInEmsg;

        public ManifestExpiryEventInfo(long eventTimeUs, long manifestPublishTimeMsInEmsg) {
            this.eventTimeUs = eventTimeUs;
            this.manifestPublishTimeMsInEmsg = manifestPublishTimeMsInEmsg;
        }
    }
}
