package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.home.glass.GlassLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassLayoutTest {
    @Test fun arrangeFollowsTheSavedOrderAndAddsNewItemsLast() {
        assertEquals(listOf("c", "a", "b", "d"), GlassLayout.arrange(listOf("a", "b", "c", "d"), listOf("c", "a")))
    }

    @Test fun arrangeForgetsRemovedItemsAndDuplicates() {
        assertEquals(listOf("b", "a"), GlassLayout.arrange(listOf("a", "b"), listOf("gone", "b", "b")))
    }

    @Test fun moveGoesForwardAndBack() {
        val items = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), GlassLayout.move(items, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), GlassLayout.move(items, "d", 0))
    }

    @Test fun moveClampsAndIgnoresUnknownItems() {
        val items = listOf("a", "b")
        assertEquals(listOf("b", "a"), GlassLayout.move(items, "a", 99))
        assertEquals(listOf("a", "b"), GlassLayout.move(items, "a", -5))
        assertEquals(items, GlassLayout.move(items, "z", 0))
    }

    @Test fun firstPageLeavesRoomForWidgets() {
        val items = (1..10).toList()
        assertEquals(listOf(listOf(1, 2), listOf(3, 4, 5, 6), listOf(7, 8, 9, 10)), GlassLayout.pages(items, 2, 4))
    }

    @Test fun thereIsAlwaysAFirstPage() {
        assertEquals(listOf(emptyList<Int>()), GlassLayout.pages(emptyList<Int>(), 4, 4))
        assertEquals(listOf(emptyList(), listOf(1, 2)), GlassLayout.pages(listOf(1, 2), 0, 4))
    }

    @Test fun slotAtFindsTheCellAndStaysInRange() {
        assertEquals(0, GlassLayout.slotAt(10f, 10f, 100f, 100f, 4, 8))
        assertEquals(5, GlassLayout.slotAt(150f, 150f, 100f, 100f, 4, 8))
        assertEquals(7, GlassLayout.slotAt(999f, 999f, 100f, 100f, 4, 8))
        assertEquals(0, GlassLayout.slotAt(-50f, -50f, 100f, 100f, 4, 8))
        assertEquals(0, GlassLayout.slotAt(50f, 50f, 100f, 100f, 4, 0))
    }

    @Test fun movingToTheNextPageLandsOnItsFirstSlot() {
        val moved = GlassLayout.moveToAdjacentPage(listOf(1, 2, 3, 4, 5, 6), 2, 0, 3, 1)
        assertEquals(listOf(listOf(1, 3, 4), listOf(2, 5, 6)), GlassLayout.pages(moved, 3, 3))
    }

    @Test fun movingToThePreviousPageLandsOnItsLastSlot() {
        val moved = GlassLayout.moveToAdjacentPage(listOf(1, 2, 3, 4, 5, 6), 5, 3, 3, -1)
        assertEquals(listOf(listOf(1, 2, 5), listOf(3, 4, 6)), GlassLayout.pages(moved, 3, 3))
    }
}
