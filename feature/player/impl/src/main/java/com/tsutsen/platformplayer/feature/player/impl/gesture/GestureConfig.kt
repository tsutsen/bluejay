package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * One sector's gesture → action mapping (4 slots).
 */
data class GestureSlotConfig(
    val swipeVertical: GestureAction = GestureAction.NONE,
    val swipeHorizontal: GestureAction = GestureAction.NONE,
    val doubleTap: GestureAction = GestureAction.NONE,
    val hold: GestureAction = GestureAction.NONE
) {
    fun resolve(type: GestureType): GestureAction = when (type) {
        GestureType.SWIPE_VERTICAL -> swipeVertical
        GestureType.SWIPE_HORIZONTAL -> swipeHorizontal
        GestureType.DOUBLE_TAP -> doubleTap
        GestureType.HOLD -> hold
    }
}

/**
 * Gesture config for one overlay mode — maps each of the 9 sectors to its slot config.
 */
data class GestureConfig(
    val sectors: Map<GestureSector, GestureSlotConfig> =
        GestureSector.entries.associateWith { GestureSlotConfig() }
) {
    fun resolve(sector: GestureSector, type: GestureType): GestureAction =
        sectors[sector]?.resolve(type) ?: GestureAction.NONE

    /** Builder-style copy with one sector updated. */
    fun withSector(sector: GestureSector, slot: GestureSlotConfig) =
        copy(sectors = sectors + (sector to slot))

    /** Builder-style copy with all sectors from a map. */
    fun withSectors(entries: Map<GestureSector, GestureSlotConfig>) =
        copy(sectors = this.sectors + entries)
}

/**
 * Gesture configs for all four overlay modes.
 */
data class GestureConfigs(
    val fullscreen: GestureConfig = GestureConfig(),
    val normal: GestureConfig = GestureConfig(),
    val compact: GestureConfig = GestureConfig(),
    val floating: GestureConfig = GestureConfig()
) {
    fun forMode(mode: com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode): GestureConfig =
        when (mode) {
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FULLSCREEN -> fullscreen
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.NORMAL -> normal
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.COMPACT -> compact
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING -> floating
        }
}
