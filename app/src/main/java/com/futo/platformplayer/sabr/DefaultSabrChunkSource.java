package com.futo.platformplayer.sabr;

import static androidx.media3.common.util.Util.addWithOverflowDefault;
import static androidx.media3.common.util.Util.subtractWithOverflowDefault;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.exoplayer.source.BehindLiveWindowException;
import androidx.media3.exoplayer.source.chunk.BaseMediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.ChunkHolder;
import androidx.media3.exoplayer.source.chunk.ContainerMediaChunk;
import androidx.media3.exoplayer.source.chunk.InitializationChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.SingleSampleMediaChunk;
import com.futo.platformplayer.sabr.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.futo.platformplayer.sabr.manifest.AdaptationSet;
import com.futo.platformplayer.sabr.manifest.RangedUri;
import com.futo.platformplayer.sabr.manifest.Representation;
import com.futo.platformplayer.sabr.manifest.SabrManifest;
import com.futo.platformplayer.sabr.parser.SabrExtractor;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class DefaultSabrChunkSource implements SabrChunkSource {
    public static final class Factory implements SabrChunkSource.Factory {

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;

        public Factory(DataSource.Factory dataSourceFactory) {
            this(dataSourceFactory, 1);
        }

        public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
        }

        @Override
        public SabrChunkSource createSabrChunkSource(
                LoaderErrorThrower manifestLoaderErrorThrower,
                SabrManifest manifest,
                int periodIndex,
                int[] adaptationSetIndices,
                ExoTrackSelection trackSelection,
                int type,
                long elapsedRealtimeOffsetMs,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
                @Nullable TransferListener transferListener) {
            DataSource dataSource = dataSourceFactory.createDataSource();
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return new DefaultSabrChunkSource(
                    manifestLoaderErrorThrower,
                    manifest,
                    periodIndex,
                    adaptationSetIndices,
                    trackSelection,
                    type,
                    dataSource,
                    elapsedRealtimeOffsetMs,
                    maxSegmentsPerLoad,
                    enableEventMessageTrack,
                    closedCaptionFormats,
                    playerEmsgHandler);
        }

    }

    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final int[] adaptationSetIndices;
    private final int trackType;
    private final DataSource dataSource;
    private final long elapsedRealtimeOffsetMs;
    private final int maxSegmentsPerLoad;
    @Nullable private final PlayerTrackEmsgHandler playerTrackEmsgHandler;

    protected final RepresentationHolder[] representationHolders;

    private ExoTrackSelection trackSelection;
    private SabrManifest manifest;
    private int periodIndex;
    private IOException fatalError;
    private boolean missingLastSegment;
    private long liveEdgeTimeUs;

    /**
     * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
     * @param manifest The initial manifest.
     * @param periodIndex The index of the period in the manifest.
     * @param adaptationSetIndices The indices of the adaptation sets in the period.
     * @param trackSelection The track selection.
     * @param trackType The type of the tracks in the selection.
     * @param dataSource A {@link DataSource} suitable for loading the media data.
     * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
     *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
     *     as the server's unix time minus the local elapsed time. If unknown, set to 0.
     * @param maxSegmentsPerLoad The maximum number of segments to combine into a single request. Note
     *     that segments will only be combined if their {@link Uri}s are the same and if their data
     *     ranges are adjacent.
     * @param enableEventMessageTrack Whether to output an event message track.
     * @param closedCaptionFormats The {@link Format Formats} of closed caption tracks to be output.
     * @param playerTrackEmsgHandler The {@link PlayerTrackEmsgHandler} instance to handle emsg
     *     messages targeting the player. Maybe null if this is not necessary.
     */
    public DefaultSabrChunkSource(
            LoaderErrorThrower manifestLoaderErrorThrower,
            SabrManifest manifest,
            int periodIndex,
            int[] adaptationSetIndices,
            ExoTrackSelection trackSelection,
            int trackType,
            DataSource dataSource,
            long elapsedRealtimeOffsetMs,
            int maxSegmentsPerLoad,
            boolean enableEventMessageTrack,
            List<Format> closedCaptionFormats,
            @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler) {
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.manifest = manifest;
        this.adaptationSetIndices = adaptationSetIndices;
        this.trackSelection = trackSelection;
        this.trackType = trackType;
        this.dataSource = dataSource;
        this.periodIndex = periodIndex;
        this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
        this.maxSegmentsPerLoad = maxSegmentsPerLoad;
        this.playerTrackEmsgHandler = playerTrackEmsgHandler;

        long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
        liveEdgeTimeUs = C.TIME_UNSET;

        List<Representation> representations = getRepresentations();
        representationHolders = new RepresentationHolder[trackSelection.length()];
        for (int i = 0; i < representationHolders.length; i++) {
            Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
            representationHolders[i] =
                    new RepresentationHolder(
                            periodDurationUs,
                            trackType,
                            representation,
                            enableEventMessageTrack,
                            closedCaptionFormats,
                            playerTrackEmsgHandler);
        }
    }

    @Override
    public void updateManifest(SabrManifest newManifest, int newPeriodIndex) {
        try {
            manifest = newManifest;
            periodIndex = newPeriodIndex;
            long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
            List<Representation> representations = getRepresentations();
            for (int i = 0; i < representationHolders.length; i++) {
                Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
                representationHolders[i] =
                        representationHolders[i].copyWithNewRepresentation(periodDurationUs, representation);
            }
        } catch (BehindLiveWindowException e) {
            fatalError = e;
        }
    }

    @Override
    public void updateTrackSelection(ExoTrackSelection trackSelection) {
        this.trackSelection = trackSelection;
    }


    /**
     * Resolves a seek given the requested seek position, a {@link SeekParameters} and two candidate
     * sync points.
     *
     * @param positionUs The requested seek position, in microseocnds.
     * @param seekParameters The {@link SeekParameters}.
     * @param firstSyncUs The first candidate seek point, in micrseconds.
     * @param secondSyncUs The second candidate seek point, in microseconds. May equal {@code
     *     firstSyncUs} if there's only one candidate.
     * @return The resolved seek position, in microseconds.
     */
    public static long resolveSeekPositionUs(
            long positionUs, SeekParameters seekParameters, long firstSyncUs, long secondSyncUs) {
        if (SeekParameters.EXACT.equals(seekParameters)) {
            return positionUs;
        }
        long minPositionUs = subtractWithOverflowDefault(positionUs, seekParameters.toleranceBeforeUs, Long.MIN_VALUE);
        long maxPositionUs = addWithOverflowDefault(positionUs, seekParameters.toleranceAfterUs, Long.MAX_VALUE);
        boolean firstSyncPositionValid = minPositionUs <= firstSyncUs && firstSyncUs <= maxPositionUs;
        boolean secondSyncPositionValid =
                minPositionUs <= secondSyncUs && secondSyncUs <= maxPositionUs;
        if (firstSyncPositionValid && secondSyncPositionValid) {
            if (Math.abs(firstSyncUs - positionUs) <= Math.abs(secondSyncUs - positionUs)) {
                return firstSyncUs;
            } else {
                return secondSyncUs;
            }
        } else if (firstSyncPositionValid) {
            return firstSyncUs;
        } else if (secondSyncPositionValid) {
            return secondSyncUs;
        } else {
            return minPositionUs;
        }
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        // Segments are aligned across representations, so any segment index will do.
        for (RepresentationHolder representationHolder : representationHolders) {
            if (representationHolder.segmentIndex != null) {
                long segmentNum = representationHolder.getSegmentNum(positionUs);
                long firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum);
                long secondSyncUs =
                        firstSyncUs < positionUs && segmentNum < representationHolder.getSegmentCount() - 1
                                ? representationHolder.getSegmentStartTimeUs(segmentNum + 1)
                                : firstSyncUs;
                return resolveSeekPositionUs(positionUs, seekParameters, firstSyncUs, secondSyncUs);
            }
        }
        // We don't have a segment index to adjust the seek position with yet.
        return positionUs;
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (fatalError != null) {
            throw fatalError;
        } else {
            manifestLoaderErrorThrower.maybeThrowError();
        }
    }

    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        if (fatalError != null || trackSelection.length() < 2) {
            return queue.size();
        }
        return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
    }

    @Override
    public void getNextChunk(LoadingInfo loadingInfo, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {
        //public void getNextChunk(long playbackPositionUs, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {
        if (fatalError != null) {
            return;
        }

        long bufferedDurationUs = loadPositionUs - loadingInfo.playbackPositionUs;
        long timeToLiveEdgeUs = resolveTimeToLiveEdgeUs(loadingInfo.playbackPositionUs);
        long presentationPositionUs = C.msToUs(manifest.availabilityStartTimeMs)
            + C.msToUs(manifest.getPeriod(periodIndex).startMs)
            + loadPositionUs;

        if (playerTrackEmsgHandler != null
                && playerTrackEmsgHandler.maybeRefreshManifestBeforeLoadingNextChunk(
                presentationPositionUs)) {
            return;
        }

        long nowUnixTimeUs = getNowUnixTimeUs();
        MediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
        MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
        for (int i = 0; i < chunkIterators.length; i++) {
            RepresentationHolder representationHolder = representationHolders[i];
            if (representationHolder.segmentIndex == null) {
                chunkIterators[i] = MediaChunkIterator.EMPTY;
            } else {
                long firstAvailableSegmentNum =
                        representationHolder.getFirstAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
                long lastAvailableSegmentNum =
                        representationHolder.getLastAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
                long segmentNum =
                        getSegmentNum(
                                representationHolder,
                                previous,
                                loadPositionUs,
                                firstAvailableSegmentNum,
                                lastAvailableSegmentNum);
                if (segmentNum < firstAvailableSegmentNum) {
                    chunkIterators[i] = MediaChunkIterator.EMPTY;
                } else {
                    chunkIterators[i] =
                            new RepresentationSegmentIterator(
                                    representationHolder, segmentNum, lastAvailableSegmentNum);
                }
            }
        }

        trackSelection.updateSelectedTrack(
                loadingInfo.playbackPositionUs, bufferedDurationUs, timeToLiveEdgeUs, queue, chunkIterators);

        RepresentationHolder representationHolder =
                representationHolders[trackSelection.getSelectedIndex()];

        if (representationHolder.extractorWrapper != null) {
            Representation selectedRepresentation = representationHolder.representation;
            RangedUri pendingInitializationUri = null;
            RangedUri pendingIndexUri = null;
            if (representationHolder.extractorWrapper.getSampleFormats() == null) {
                pendingInitializationUri = selectedRepresentation.getInitializationUri();
            }
            if (representationHolder.segmentIndex == null) {
                pendingIndexUri = selectedRepresentation.getIndexUri();
            }
            if (pendingInitializationUri != null || pendingIndexUri != null) {
                // We have initialization and/or index requests to make.
                out.chunk = newInitializationChunk(representationHolder, dataSource,
                        trackSelection.getSelectedFormat(), trackSelection.getSelectionReason(),
                        trackSelection.getSelectionData(), pendingInitializationUri, pendingIndexUri);
                return;
            }
        }

        long periodDurationUs = representationHolder.periodDurationUs;
        boolean periodEnded = periodDurationUs != C.TIME_UNSET;

        if (representationHolder.getSegmentCount() == 0) {
            // The index doesn't define any segments.
            out.endOfStream = periodEnded;
            return;
        }

        long firstAvailableSegmentNum =
                representationHolder.getFirstAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
        long lastAvailableSegmentNum =
                representationHolder.getLastAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);

        updateLiveEdgeTimeUs(representationHolder, lastAvailableSegmentNum);

        long segmentNum =
                getSegmentNum(
                        representationHolder,
                        previous,
                        loadPositionUs,
                        firstAvailableSegmentNum,
                        lastAvailableSegmentNum);
        if (segmentNum < firstAvailableSegmentNum) {
            // This is before the first chunk in the current manifest.
            fatalError = new BehindLiveWindowException();
            return;
        }

        if (segmentNum > lastAvailableSegmentNum
                || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
            // The segment is beyond the end of the period.
            out.endOfStream = periodEnded;
            return;
        }

        if (periodEnded && representationHolder.getSegmentStartTimeUs(segmentNum) >= periodDurationUs) {
            // The period duration clips the period to a position before the segment.
            out.endOfStream = true;
            return;
        }

        int maxSegmentCount =
                (int) Math.min(maxSegmentsPerLoad, lastAvailableSegmentNum - segmentNum + 1);
        if (periodDurationUs != C.TIME_UNSET) {
            while (maxSegmentCount > 1
                    && representationHolder.getSegmentStartTimeUs(segmentNum + maxSegmentCount - 1)
                    >= periodDurationUs) {
                // The period duration clips the period to a position before the last segment in the range
                // [segmentNum, segmentNum + maxSegmentCount - 1]. Reduce maxSegmentCount.
                maxSegmentCount--;
            }
        }

        long seekTimeUs = queue.isEmpty() ? loadPositionUs : C.TIME_UNSET;
        out.chunk =
                newMediaChunk(
                        representationHolder,
                        dataSource,
                        trackType,
                        trackSelection.getSelectedFormat(),
                        trackSelection.getSelectionReason(),
                        trackSelection.getSelectionData(),
                        segmentNum,
                        maxSegmentCount,
                        seekTimeUs);
    }

    @Override
    public boolean shouldCancelLoad(
            long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
        if (fatalError != null || trackSelection.length() < 2) {
            return false;
        }
        // Let the selection decide (Media3 exposes this).
        return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, (List<MediaChunk>) queue);
    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        // If the init chunk just finished, try to grab a parsed ChunkIndex from the extractor.
        if (chunk instanceof InitializationChunk) {
            final int trackIndex = trackSelection.indexOf(chunk.trackFormat);
            if (trackIndex != C.INDEX_UNSET) {
                RepresentationHolder holder = representationHolders[trackIndex];

                // Don't overwrite a manifest-defined index. Only adopt stream-provided index if needed.
                if (holder.segmentIndex == null && holder.extractorWrapper != null) {
                    // Media3 exposes the parsed index via ChunkExtractor.getChunkIndex() now.
                    ChunkIndex chunkIndex = holder.extractorWrapper.getChunkIndex();
                    if (chunkIndex != null) {
                        representationHolders[trackIndex] =
                                holder.copyWithNewSegmentIndex(
                                        new SabrWrappingSegmentIndex(
                                                chunkIndex,
                                                holder.representation.presentationTimeOffsetUs));
                    }
                }
            }
        }

        if (playerTrackEmsgHandler != null) {
            playerTrackEmsgHandler.onChunkLoadCompleted(chunk);
        }
    }

    @Override
    public boolean onChunkLoadError(
            Chunk chunk,
            boolean cancelable,
            androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo,
            androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        if (!cancelable) return false;

        // Manifest-driven refresh (same behavior you had before).
        if (playerTrackEmsgHandler != null && playerTrackEmsgHandler.maybeRefreshManifestOnLoadingError(chunk)) {
            return true; // cancel & re-resolve next chunk
        }

        // Workaround for a missing last segment on VOD (404) — unchanged logic, updated signature.
        if (!manifest.dynamic
                && chunk instanceof MediaChunk
                && loadErrorInfo.exception instanceof InvalidResponseCodeException
                && ((InvalidResponseCodeException) loadErrorInfo.exception).responseCode == 404) {
            RepresentationHolder holder = representationHolders[trackSelection.indexOf(chunk.trackFormat)];
            int count = holder.getSegmentCount();
            if (count != SabrSegmentIndex.INDEX_UNBOUNDED && count != 0) {
                long lastAvailable = holder.getFirstSegmentNum() + count - 1;
                if (((MediaChunk) chunk).getNextChunkIndex() > lastAvailable) {
                    missingLastSegment = true;
                    return true; // cancel; we’ll end the period gracefully
                }
            }
        }

        // Modern fallback track exclusion using LoadErrorHandlingPolicy
        int excluded = 0;
        long nowMs = SystemClock.elapsedRealtime();
        for (int i = 0; i < trackSelection.length(); i++) {
            if (trackSelection.isTrackExcluded(i, nowMs)) excluded++;
        }
        androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions options =
                new androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions(
                        /* numberOfLocations= */ 1, /* numberOfExcludedLocations= */ 0,
                        /* numberOfTracks= */ trackSelection.length(), /* numberOfExcludedTracks= */ excluded);

        androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection sel =
                loadErrorHandlingPolicy.getFallbackSelectionFor(options, loadErrorInfo);

        if (sel != null
                && sel.type == androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
            int trackIdx = trackSelection.indexOf(chunk.trackFormat);
            return trackSelection.excludeTrack(trackIdx, sel.exclusionDurationMs);
        }

        return false;
    }

    private ArrayList<Representation> getRepresentations() {
        List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        ArrayList<Representation> representations = new ArrayList<>();
        for (int adaptationSetIndex : adaptationSetIndices) {
            representations.addAll(manifestAdaptationSets.get(adaptationSetIndex).representations);
        }
        return representations;
    }

    private long resolveTimeToLiveEdgeUs(long playbackPositionUs) {
        boolean resolveTimeToLiveEdgePossible = manifest.dynamic && liveEdgeTimeUs != C.TIME_UNSET;
        return resolveTimeToLiveEdgePossible ? liveEdgeTimeUs - playbackPositionUs : C.TIME_UNSET;
    }

    private long getNowUnixTimeUs() {
        if (elapsedRealtimeOffsetMs != 0) {
            return (SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs) * 1000;
        } else {
            return System.currentTimeMillis() * 1000;
        }
    }

    @Override
    public void release() {
        // Forward-looking: free extractor resources if needed.
        for (RepresentationHolder h : representationHolders) {
            if (h != null && h.extractorWrapper != null) {
                h.extractorWrapper.release();
            }
        }
    }

    private long getSegmentNum(
            RepresentationHolder representationHolder,
            @Nullable MediaChunk previousChunk,
            long loadPositionUs,
            long firstAvailableSegmentNum,
            long lastAvailableSegmentNum) {
        return previousChunk != null
                ? previousChunk.getNextChunkIndex()
                : Util.constrainValue(representationHolder.getSegmentNum(loadPositionUs), firstAvailableSegmentNum, lastAvailableSegmentNum);
    }

    private void updateLiveEdgeTimeUs(
            RepresentationHolder representationHolder, long lastAvailableSegmentNum) {
        liveEdgeTimeUs = manifest.dynamic
                ? representationHolder.getSegmentEndTimeUs(lastAvailableSegmentNum) : C.TIME_UNSET;
    }

    protected Chunk newInitializationChunk(
            RepresentationHolder representationHolder,
            DataSource dataSource,
            Format trackFormat,
            int trackSelectionReason,
            Object trackSelectionData,
            RangedUri initializationUri,
            RangedUri indexUri) {
        RangedUri requestUri;
        String baseUrl = representationHolder.representation.baseUrl;
        if (initializationUri != null) {
            // It's common for initialization and index data to be stored adjacently. Attempt to merge
            // the two requests together to request both at once.
            requestUri = initializationUri.attemptMerge(indexUri, baseUrl);
            if (requestUri == null) {
                requestUri = initializationUri;
            }
        } else {
            requestUri = indexUri;
        }
        // TODO: first protobuf request (before the video start off)
        DataSpec dataSpec = new DataSpec(requestUri.resolveUri(baseUrl), requestUri.start,
                requestUri.length, representationHolder.representation.getCacheKey());
        return new InitializationChunk(dataSource, dataSpec, trackFormat,
                trackSelectionReason, trackSelectionData, representationHolder.extractorWrapper);
    }

    protected Chunk newMediaChunk(
            RepresentationHolder representationHolder,
            DataSource dataSource,
            int trackType,
            Format trackFormat,
            int trackSelectionReason,
            Object trackSelectionData,
            long firstSegmentNum,
            int maxSegmentCount,
            long seekTimeUs) {
        Representation representation = representationHolder.representation;
        long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
        RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
        String baseUrl = representation.baseUrl;
        if (representationHolder.extractorWrapper == null) {
            long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
            DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
                    segmentUri.start, segmentUri.length, representation.getCacheKey());
            return new SingleSampleMediaChunk(dataSource, dataSpec, trackFormat, trackSelectionReason,
                    trackSelectionData, startTimeUs, endTimeUs, firstSegmentNum, trackType, trackFormat);
        } else {
            int segmentCount = 1;
            for (int i = 1; i < maxSegmentCount; i++) {
                RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
                RangedUri mergedSegmentUri = segmentUri.attemptMerge(nextSegmentUri, baseUrl);
                if (mergedSegmentUri == null) {
                    // Unable to merge segment fetches because the URIs do not merge.
                    break;
                }
                segmentUri = mergedSegmentUri;
                segmentCount++;
            }
            long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum + segmentCount - 1);
            long periodDurationUs = representationHolder.periodDurationUs;
            long clippedEndTimeUs =
                    periodDurationUs != C.TIME_UNSET && periodDurationUs <= endTimeUs
                            ? periodDurationUs
                            : C.TIME_UNSET;
            // TODO: next protobuf requests (during the playback)
            DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
                    segmentUri.start, segmentUri.length, representation.getCacheKey());
            long sampleOffsetUs = -representation.presentationTimeOffsetUs;
            return new ContainerMediaChunk(
                    dataSource,
                    dataSpec,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    startTimeUs,
                    endTimeUs,
                    seekTimeUs,
                    clippedEndTimeUs,
                    firstSegmentNum,
                    segmentCount,
                    sampleOffsetUs,
                    representationHolder.extractorWrapper);
        }
    }

    /** {@link MediaChunkIterator} wrapping a {@link RepresentationHolder}. */
    protected static final class RepresentationSegmentIterator extends BaseMediaChunkIterator {

        private final RepresentationHolder representationHolder;

        /**
         * Creates iterator.
         *
         * @param representation The {@link RepresentationHolder} to wrap.
         * @param firstAvailableSegmentNum The number of the first available segment.
         * @param lastAvailableSegmentNum The number of the last available segment.
         */
        public RepresentationSegmentIterator(
                RepresentationHolder representation,
                long firstAvailableSegmentNum,
                long lastAvailableSegmentNum) {
            super(/* fromIndex= */ firstAvailableSegmentNum, /* toIndex= */ lastAvailableSegmentNum);
            this.representationHolder = representation;
        }

        @Override
        public DataSpec getDataSpec() {
            checkInBounds();
            Representation representation = representationHolder.representation;
            RangedUri segmentUri = representationHolder.getSegmentUrl(getCurrentIndex());
            Uri resolvedUri = segmentUri.resolveUri(representation.baseUrl);
            String cacheKey = representation.getCacheKey();
            return new DataSpec(resolvedUri, segmentUri.start, segmentUri.length, cacheKey);
        }

        @Override
        public long getChunkStartTimeUs() {
            checkInBounds();
            return representationHolder.getSegmentStartTimeUs(getCurrentIndex());
        }

        @Override
        public long getChunkEndTimeUs() {
            checkInBounds();
            return representationHolder.getSegmentEndTimeUs(getCurrentIndex());
        }
    }

    /** Holds information about a snapshot of a single {@link Representation}. */
    protected static final class RepresentationHolder {

        /* package */ final @Nullable ChunkExtractor extractorWrapper;

        public final Representation representation;
        public final @Nullable SabrSegmentIndex segmentIndex;

        private final long periodDurationUs;
        private final long segmentNumShift;

        /* package */ RepresentationHolder(
                long periodDurationUs,
                int trackType,
                Representation representation,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                TrackOutput playerEmsgTrackOutput) {
            this(
                    periodDurationUs,
                    representation,
                    createExtractorWrapper(
                            trackType,
                            representation,
                            enableEventMessageTrack,
                            closedCaptionFormats,
                            playerEmsgTrackOutput),
                    /* segmentNumShift= */ 0,
                    representation.getIndex());
        }

        private RepresentationHolder(
                long periodDurationUs,
                Representation representation,
                @Nullable ChunkExtractor extractorWrapper,
                long segmentNumShift,
                @Nullable SabrSegmentIndex segmentIndex) {
            this.periodDurationUs = periodDurationUs;
            this.representation = representation;
            this.segmentNumShift = segmentNumShift;
            this.extractorWrapper = extractorWrapper;
            this.segmentIndex = segmentIndex;
        }

        @CheckResult
            /* package */ RepresentationHolder copyWithNewRepresentation(
                long newPeriodDurationUs, Representation newRepresentation)
                throws BehindLiveWindowException {
            SabrSegmentIndex oldIndex = representation.getIndex();
            SabrSegmentIndex newIndex = newRepresentation.getIndex();

            if (oldIndex == null) {
                // Segment numbers cannot shift if the index isn't defined by the manifest.
                return new RepresentationHolder(
                        newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, oldIndex);
            }

            if (!oldIndex.isExplicit()) {
                // Segment numbers cannot shift if the index isn't explicit.
                return new RepresentationHolder(
                        newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, newIndex);
            }

            int oldIndexSegmentCount = oldIndex.getSegmentCount(newPeriodDurationUs);
            if (oldIndexSegmentCount == 0) {
                // Segment numbers cannot shift if the old index was empty.
                return new RepresentationHolder(
                        newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, newIndex);
            }

            long oldIndexFirstSegmentNum = oldIndex.getFirstSegmentNum();
            long oldIndexStartTimeUs = oldIndex.getTimeUs(oldIndexFirstSegmentNum);
            long oldIndexLastSegmentNum = oldIndexFirstSegmentNum + oldIndexSegmentCount - 1;
            long oldIndexEndTimeUs =
                    oldIndex.getTimeUs(oldIndexLastSegmentNum)
                            + oldIndex.getDurationUs(oldIndexLastSegmentNum, newPeriodDurationUs);
            long newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
            long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
            long newSegmentNumShift = segmentNumShift;
            if (oldIndexEndTimeUs == newIndexStartTimeUs) {
                // The new index continues where the old one ended, with no overlap.
                newSegmentNumShift += oldIndexLastSegmentNum + 1 - newIndexFirstSegmentNum;
            } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
                // There's a gap between the old index and the new one which means we've slipped behind the
                // live window and can't proceed.
                throw new BehindLiveWindowException();
            } else if (newIndexStartTimeUs < oldIndexStartTimeUs) {
                // The new index overlaps with (but does not have a start position contained within) the old
                // index. This can only happen if extra segments have been added to the start of the index.
                newSegmentNumShift -=
                        newIndex.getSegmentNum(oldIndexStartTimeUs, newPeriodDurationUs)
                                - oldIndexFirstSegmentNum;
            } else {
                // The new index overlaps with (and has a start position contained within) the old index.
                newSegmentNumShift +=
                        oldIndex.getSegmentNum(newIndexStartTimeUs, newPeriodDurationUs)
                                - newIndexFirstSegmentNum;
            }
            return new RepresentationHolder(
                    newPeriodDurationUs, newRepresentation, extractorWrapper, newSegmentNumShift, newIndex);
        }

        @CheckResult
            /* package */ RepresentationHolder copyWithNewSegmentIndex(SabrSegmentIndex segmentIndex) {
            return new RepresentationHolder(
                    periodDurationUs, representation, extractorWrapper, segmentNumShift, segmentIndex);
        }

        public long getFirstSegmentNum() {
            return segmentIndex.getFirstSegmentNum() + segmentNumShift;
        }

        public int getSegmentCount() {
            return segmentIndex.getSegmentCount(periodDurationUs);
        }

        public long getSegmentStartTimeUs(long segmentNum) {
            return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
        }

        public long getSegmentEndTimeUs(long segmentNum) {
            return getSegmentStartTimeUs(segmentNum)
                    + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
        }

        public long getSegmentNum(long positionUs) {
            return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
        }

        public RangedUri getSegmentUrl(long segmentNum) {
            return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
        }

        public long getFirstAvailableSegmentNum(
                SabrManifest manifest, int periodIndex, long nowUnixTimeUs) {
            if (getSegmentCount() == SabrSegmentIndex.INDEX_UNBOUNDED
                    && manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
                // The index is itself unbounded. We need to use the current time to calculate the range of
                // available segments.
                long liveEdgeTimeUs = nowUnixTimeUs - C.msToUs(manifest.availabilityStartTimeMs);
                long periodStartUs = C.msToUs(manifest.getPeriod(periodIndex).startMs);
                long liveEdgeTimeInPeriodUs = liveEdgeTimeUs - periodStartUs;
                long bufferDepthUs = C.msToUs(manifest.timeShiftBufferDepthMs);
                return Math.max(
                        getFirstSegmentNum(), getSegmentNum(liveEdgeTimeInPeriodUs - bufferDepthUs));
            }
            return getFirstSegmentNum();
        }

        public long getLastAvailableSegmentNum(
                SabrManifest manifest, int periodIndex, long nowUnixTimeUs) {
            int availableSegmentCount = getSegmentCount();
            if (availableSegmentCount == SabrSegmentIndex.INDEX_UNBOUNDED) {
                // The index is itself unbounded. We need to use the current time to calculate the range of
                // available segments.
                long liveEdgeTimeUs = nowUnixTimeUs - C.msToUs(manifest.availabilityStartTimeMs);
                long periodStartUs = C.msToUs(manifest.getPeriod(periodIndex).startMs);
                long liveEdgeTimeInPeriodUs = liveEdgeTimeUs - periodStartUs;
                // getSegmentNum(liveEdgeTimeInPeriodUs) will not be completed yet, so subtract one to get
                // the index of the last completed segment.
                return getSegmentNum(liveEdgeTimeInPeriodUs) - 1;
            }
            return getFirstSegmentNum() + availableSegmentCount - 1;
        }

        private static boolean mimeTypeIsWebm(String mimeType) {
            return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
                    || mimeType.startsWith(MimeTypes.APPLICATION_WEBM);
        }

        private static boolean mimeTypeIsRawText(String mimeType) {
            return MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType);
        }


        private static @Nullable ChunkExtractor createExtractorWrapper(
                int trackType,
                Representation representation,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                TrackOutput playerEmsgTrackOutput) {

            String containerMimeType = representation.format.containerMimeType;
            if (mimeTypeIsRawText(containerMimeType)) {
                return null;
            }

            Extractor extractor = new SabrExtractor(trackType, representation.format);
            return new BundledChunkExtractor(extractor, trackType, representation.format);
        }
    }
}
