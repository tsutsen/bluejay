package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weighted-spawn merge in [HomebrewRecommendations.interleave]: dedupe by
 * card id, zero-weight sources contribute nothing, and the weights skew the
 * pick distribution.
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
        // P(pick b) per item is ~1%; getting more than a handful from b is
        // astronomically unlikely, so this margin cannot flake in practice.
        assertTrue(ids(out).count { it.startsWith("b") } < 20)
    }
}
