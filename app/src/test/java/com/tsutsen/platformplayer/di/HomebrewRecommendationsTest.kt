package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weighted-spawn merge in [HomebrewRecommendations.interleave]: dedupe by
 * card id, zero-weight sources contribute nothing, and the weights skew the
 * pick distribution. Also covers [HomebrewRecommendations.spawnWeights]:
 * the cut points split 0..100 across the ENABLED sources only, so the enabled
 * weights always sum to 1 and disabled sources get 0.
 */
class HomebrewRecommendationsTest {
    private fun card(id: String): Card =
        VideoCard(id = id, title = id, thumbnailUrl = null, author = null, url = "https://youtu.be/$id")

    private fun ids(cards: List<Card>): List<String> = cards.map { it.id }

    @Test
    fun interleaveEmitsEveryUniqueCardDedupedById() {
        val a = listOf(card("1"), card("2"), card("3"))
        val b = listOf(card("2"), card("4"), card("1"))
        val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(1f, 1f))
        assertEquals(4, out.size)
        assertEquals(setOf("1", "2", "3", "4"), out.map { it.id }.toSet())
    }

    @Test
    fun zeroWeightSourceContributesNothing() {
        val a = listOf(card("1"))
        val b = listOf(card("2"), card("3"))
        val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(0f, 1f))
        assertEquals(listOf("2", "3"), ids(out))
    }

    @Test
    fun allZeroWeightsYieldEmptyFeed() {
        val out =
            HomebrewRecommendations.interleave(
                listOf(listOf(card("1")), listOf(card("2"))),
                listOf(0f, 0f),
            )
        assertTrue(out.isEmpty())
    }

    @Test
    fun heavyWeightSourceDominates() {
        val a = (1..100).map { card("a$it") }
        val b = (1..100).map { card("b$it") }
        val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(1f, 0.01f))
        assertEquals(200, out.size)
        // P(pick b) per item is ~1%. Only the head matters: once source a is
        // exhausted every remaining pick is b, so the tail is all b's.
        // P(>= 10 b's in the first 60 picks at p~1%) is < 1e-8 — cannot flake.
        assertTrue(ids(out).take(60).count { it.startsWith("b") } < 10)
    }

    @Test
    fun spawnWeightsDefaultCutsSplitIntoThirds() {
        val w = HomebrewRecommendations.spawnWeights(Settings.RecommendationsSettings())
        assertEquals(3, w.size)
        assertEquals(0.33f, w[0], 0.0001f)
        assertEquals(0.33f, w[1], 0.0001f)
        assertEquals(0.34f, w[2], 0.0001f)
        assertEquals(1f, w.sum(), 0.0001f)
    }

    @Test
    fun spawnWeightsCutsMapOntoEnabledSourcesOnly() {
        val s =
            Settings.RecommendationsSettings().apply {
                probCut1 = 20
                probCut2 = 60
                sourceSubscriptions = false
            }
        val w = HomebrewRecommendations.spawnWeights(s)
        // Two enabled sources (youtube, topCreators): the first cut (20)
        // splits them — youtube 20%, top creators 80%, subs 0.
        assertEquals(0.2f, w[0], 0.0001f)
        assertEquals(0f, w[1], 0.0001f)
        assertEquals(0.8f, w[2], 0.0001f)
        assertEquals(1f, w.sum(), 0.0001f)
    }

    @Test
    fun spawnWeightsAllSourcesDisabledYieldZero() {
        val s =
            Settings.RecommendationsSettings().apply {
                sourceYoutubeFeed = false
                sourceSubscriptions = false
                sourceTopCreators = false
            }
        assertEquals(listOf(0f, 0f, 0f), HomebrewRecommendations.spawnWeights(s))
    }

    @Test
    fun spawnWeightsInvertedCutsAreCoerced() {
        val s =
            Settings.RecommendationsSettings().apply {
                probCut1 = 80
                probCut2 = 10
            }
        val w = HomebrewRecommendations.spawnWeights(s)
        // Cuts are sorted: [10, 80] -> thirds-style segments [0.1, 0.7, 0.2].
        assertEquals(0.1f, w[0], 0.0001f)
        assertEquals(0.7f, w[1], 0.0001f)
        assertEquals(0.2f, w[2], 0.0001f)
        assertEquals(1f, w.sum(), 0.0001f)
    }

    private fun vcard(
        id: String,
        author: String? = null,
        authorUrl: String? = null,
    ): Card =
        VideoCard(
            id = id,
            title = id,
            thumbnailUrl = null,
            author = author,
            authorUrl = authorUrl,
            url = "https://youtu.be/$id",
        )

    @Test
    fun interleaveAvoidsConsecutiveCardsFromSameCreator() {
        // Source A is all creator X, source B all creator Y: whatever the
        // roulette picks, the next card must come from the other creator
        // while both still have items, so the output strictly alternates.
        val a = (1..3).map { vcard("a$it", author = "X") }
        val b = (1..3).map { vcard("b$it", author = "Y") }
        repeat(20) {
            val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(1f, 1f))
            assertEquals(6, out.size)
            out.zipWithNext().forEach { (prev, next) ->
                assertTrue(
                    "consecutive cards from the same creator: ${prev.id}, ${next.id}",
                    prev.id[0] != next.id[0],
                )
            }
        }
    }

    @Test
    fun interleaveAcceptsSameCreatorWhenNothingElseRemains() {
        // Every candidate repeats the last creator — the pick must still be
        // emitted (no deadlock).
        val a = (1..2).map { vcard("a$it", author = "X") }
        val b = (1..2).map { vcard("b$it", author = "X") }
        val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(1f, 1f))
        assertEquals(4, out.size)
        assertEquals(setOf("a1", "a2", "b1", "b2"), out.map { it.id }.toSet())
    }

    @Test
    fun interleaveCreatorKeyPrefersAuthorUrlOverAuthor() {
        // Same author name but different authorUrl = different creators;
        // same authorUrl = same creator even when names differ.
        val a = listOf(vcard("a1", author = "Same Name", authorUrl = "https://ch/1"))
        val b = listOf(vcard("b1", author = "Same Name", authorUrl = "https://ch/1"))
        val c = listOf(vcard("c1", author = "Same Name", authorUrl = "https://ch/2"))
        val out = HomebrewRecommendations.interleave(listOf(a, b, c), listOf(1f, 1f, 1f))
        assertEquals(3, out.size)
        // a1 and b1 share a creator key: they may be adjacent only when the
        // one different creator (c1) was already emitted, i.e. the repeat was
        // forced — never while a different creator was still available.
        val idxA = out.indexOfFirst { it.id == "a1" }
        val idxB = out.indexOfFirst { it.id == "b1" }
        val idxC = out.indexOfFirst { it.id == "c1" }
        if (kotlin.math.abs(idxA - idxB) == 1) {
            assertTrue(
                "a1 and b1 adjacent while c1 was still pending: $out",
                idxC < minOf(idxA, idxB),
            )
        }
    }

    @Test
    fun interleaveStillDedupesWithAntiRepeat() {
        val a = listOf(vcard("1", author = "X"), vcard("2", author = "X"))
        val b = listOf(vcard("2", author = "Y"), vcard("3", author = "Y"))
        val out = HomebrewRecommendations.interleave(listOf(a, b), listOf(1f, 1f))
        assertEquals(3, out.size)
        assertEquals(setOf("1", "2", "3"), out.map { it.id }.toSet())
    }
}
