package com.tsutsen.platformplayer.core.model

/**
 * A downloadable quality choice.
 *
 * [targetPixelCount] is the target video resolution as width*height — the
 * engine selects the closest available source (null = best). [targetBitrate]
 * is the target audio bitrate in bits/s (null = best).
 */
data class DownloadQuality(
    val label: String,
    val targetPixelCount: Long?,
    val targetBitrate: Long?,
) {
    companion object {
        /** Qualities offered in the download quality menu, best first. */
        val Options: List<DownloadQuality> =
            listOf(
                DownloadQuality("Best", null, null),
                DownloadQuality("1080p", 1920L * 1080L, 192_000L),
                DownloadQuality("720p", 1280L * 720L, 128_000L),
                DownloadQuality("480p", 854L * 480L, 96_000L),
                DownloadQuality("360p", 640L * 360L, 64_000L),
                DownloadQuality("144p", 256L * 144L, 48_000L),
            )

        /** Pre-selected when the menu opens. */
        val Default: DownloadQuality = Options.first { it.label == "480p" }

        /** Resolve a stored resolution label (e.g. "720p") to a quality. */
        fun fromLabel(label: String): DownloadQuality =
            Options.firstOrNull { it.label == label } ?: Default
    }
}
