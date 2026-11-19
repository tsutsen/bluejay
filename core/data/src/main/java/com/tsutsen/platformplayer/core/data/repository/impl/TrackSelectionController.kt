package com.tsutsen.platformplayer.core.data.repository.impl

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.TrackSelectionValues
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The user's track preferences (UI labels) and their application to an
 * ExoPlayer instance. Extracted from [PlayerRepositoryImpl], which keeps
 * the interface methods and delegates.
 */
internal class TrackSelectionController(
    private val state: MutableStateFlow<PlayerState>,
) {
    var selectedQuality: String = TrackSelectionValues.AUTO

    var selectedSubtitle: String = TrackSelectionValues.OFF

    // Preferred audio track (UI label); null = engine default heuristics.
    var preferredAudioTrack: String? = null

    /** Last concretely selected track — used by [toggle] when re-enabling. */
    var lastExplicitSubtitle: String? = null

    /**
     * Rebuilds the player's track selection parameters from the user's
     * quality/subtitle preferences.
     *
     * Quality: "NNNp" caps the maximum video height (media3 picks the
     * highest track at or below it); "Auto" clears the cap.
     * Subtitles: "Off" ignores all text tracks, a language code prefers
     * that language, "Auto" leaves selection to the default heuristics.
     */
    fun applyTrackSelectionParameters(player: ExoPlayer?) {
        if (player == null) return
        val builder = TrackSelectionParameters.Builder()
        val height = selectedQuality.removeSuffix("p").toIntOrNull()
        if (height != null && height > 0) {
            // Constrain a narrow band around the selected height so the exact
            // quality is forced. A max-only cap would leave lower tracks
            // "acceptable" and SABR's sticky ABR would keep the current (lower)
            // format instead of stepping up. "NNNp" => [NNN-10, NNN+10]; Auto => unconstrained.
            //
            // maxWidth MUST be a real large value (9999), not -1: media3's
            // isWithinMaxConstraints checks `format.width <= maxVideoWidth`, so -1 makes
            // EVERY video track fail the max constraint. Combined with the in-band high-res
            // track being ineligible, that sends media3 down the "outside min & max ->
            // prefer lower quality" path and it collapses to 144p. Grayjay uses 9999.
            builder.setMinVideoSize(0, height - 10)
            builder.setMaxVideoSize(9999, height + 10)
        }
        when (selectedSubtitle) {
            TrackSelectionValues.OFF -> {
                builder.setIgnoredTextSelectionFlags(
                    C.SELECTION_FLAG_AUTOSELECT or C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT,
                )
            }

            TrackSelectionValues.AUTO -> {
                Unit
            }

            else -> {
                builder.setPreferredTextLanguages(selectedSubtitle)
            }
        }
        // Audio: select by language when the track carries one, by label
        // otherwise. ponytail: two tracks sharing a language resolve to the
        // first one — per-track group/track forcing (removed from the
        // Player API in media3 1.9) if that ever matters.
        preferredAudioTrack?.let { label ->
            val track = state.value.audioTracks.firstOrNull { it.label == label }
            if (track != null) {
                val language = track.language
                if (language != null) {
                    builder.setPreferredAudioLanguages(language)
                } else {
                    builder.setPreferredAudioLabels(track.label)
                }
            }
        }
        player.setTrackSelectionParameters(builder.build())
    }

    /**
     * Applies [selectedSubtitle] as a track-selection change over the
     * pre-merged subtitle sources — instant, no re-prepare (a source
     * rebuild blanked the surface and stalled playback for a beat).
     * "Off"/"Auto" disable the text renderer; a specific name overrides
     * the text track tagged "sub_<name>".
     */
    fun applySubtitleSelection(player: ExoPlayer?) {
        if (player == null) return
        val builder = player.trackSelectionParameters.buildUpon()
        val wanted = selectedSubtitle
        val targetId = if (wanted == TrackSelectionValues.OFF || wanted == TrackSelectionValues.AUTO) null else "sub_$wanted"
        var applied = false
        if (targetId != null) {
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_TEXT) continue
                for (i in 0 until group.length) {
                    if (group.getTrackFormat(i).id == targetId) {
                        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        applied = true
                    }
                }
            }
        }
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !applied)
        player.trackSelectionParameters = builder.build()
    }
}
