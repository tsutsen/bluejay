package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.structures.IPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerFlowTest {
    /**
     * Models real pager delta semantics: [getResults] returns only the last
     * loaded page; page 0 is already loaded on construction.
     */
    private class DeltaPager<T>(
        private val pages: List<List<T>>,
        private var failNext: Boolean = false,
    ) : IPager<T> {
        private var page = 0
        var nextPageCalls = 0
            private set

        override fun hasMorePages() = page < pages.size - 1

        override fun nextPage() {
            nextPageCalls++
            if (failNext && page == 0) throw IllegalStateException("pager boom")
            page++
        }

        override fun getResults() = pages[page]
    }

    @Test
    fun loadInitial_returnsMappedFirstPage() {
        val flow = PagerFlow(DeltaPager(listOf(listOf("a", "b"), listOf("c"))), { it })
        assertEquals(listOf("a", "b"), flow.loadInitial())
        assertEquals(listOf("a", "b"), flow.items)
        assertTrue(flow.hasMore)
        assertNull(flow.error)
    }

    @Test
    fun loadNextPage_appendsDelta_returnsOnlyNewItems() {
        val flow = PagerFlow(DeltaPager(listOf(listOf("a"), listOf("b", "c"))), { it })
        flow.loadInitial()
        val newItems = flow.loadNextPage()
        assertEquals(listOf("b", "c"), newItems)
        assertEquals(listOf("a", "b", "c"), flow.items)
        assertTrue(!flow.hasMore)
    }

    @Test
    fun nullMappingResults_areDropped() {
        val flow =
            PagerFlow(
                DeltaPager(listOf(listOf("keep", "drop", "keep2"))),
                { if (it == "drop") null else it },
            )
        assertEquals(listOf("keep", "keep2"), flow.loadInitial())
    }

    @Test
    fun loadNextPage_whenEngineReReturnsPreviousItems_deduplicates() {
        // Small playlists: once content runs out, the engine window re-returns
        // existing items. They must not reach [items] (duplicate lazy keys crash).
        val flow =
            PagerFlow(
                DeltaPager(listOf(listOf("a", "b"), listOf("b", "c"))),
                { it },
            )
        flow.loadInitial()
        flow.loadNextPage()
        assertEquals(listOf("a", "b", "c"), flow.items)
    }

    @Test
    fun dedupe_usesKeyNotFullEquality() {
        // Re-returned items can carry different secondary fields (e.g. a
        // thumbnail that failed on the first pass); equality dedupe would
        // miss them. Key dedupe (Card.id) must catch them.
        data class Item(
            val id: String,
            val extra: String,
        )

        val flow =
            PagerFlow(
                DeltaPager(
                    listOf(
                        listOf(Item("a", "t1")),
                        listOf(Item("a", "t2"), Item("b", "t3")),
                    ),
                ),
                { it },
                { it.id },
            )
        flow.loadInitial()
        flow.loadNextPage()
        assertEquals(listOf(Item("a", "t1"), Item("b", "t3")), flow.items)
    }

    @Test
    fun loadInitial_dropsIntraPageDuplicates() {
        val flow = PagerFlow(DeltaPager(listOf(listOf("a", "a", "b"))), { it })
        flow.loadInitial()
        assertEquals(listOf("a", "b"), flow.items)
    }

    @Test
    fun loadNextPage_whenExhausted_returnsEmpty() {
        val flow = PagerFlow(DeltaPager(listOf(listOf("a"))), { it })
        flow.loadInitial()
        assertEquals(emptyList<String>(), flow.loadNextPage())
        assertEquals(listOf("a"), flow.items)
    }

    @Test
    fun loadNextPage_failure_setsError_keepsExistingItems() {
        val flow = PagerFlow(DeltaPager(listOf(listOf("a"), listOf("b")), failNext = true), { it })
        flow.loadInitial()
        val newItems = flow.loadNextPage()
        assertEquals(emptyList<String>(), newItems)
        assertEquals(listOf("a"), flow.items)
        assertEquals("pager boom", flow.error)
    }
}
