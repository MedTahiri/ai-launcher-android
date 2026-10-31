package io.github.medtahiri.launcher.home.glass

/** The rules for arranging home screen icons, kept free of Android so that they can be unit tested. */
object GlassLayout {
    /** [items] in the [saved] order. Anything not saved yet, like a new app, goes at the end in its given order. */
    fun <T> arrange(items: List<T>, saved: List<T>): List<T> {
        val known = items.toSet()
        val inOrder = saved.distinct().filter { it in known }
        return inOrder + (items - inOrder.toSet())
    }

    /** The list with [item] moved to [index], which is clamped to the list. */
    fun <T> move(items: List<T>, item: T, index: Int): List<T> {
        if (item !in items) return items
        val rest = items - item
        val target = index.coerceIn(0, rest.size)
        return rest.take(target) + item + rest.drop(target)
    }

    /**
     * The list with [item] moved off its page: to the first slot of the next page, or the last of the previous one.
     * Pages hold a fixed number of slots, so the neighboring item takes its place on this page.
     */
    fun <T> moveToAdjacentPage(items: List<T>, item: T, pageStart: Int, pageSize: Int, direction: Int): List<T> =
        move(items, item, if (direction > 0) pageStart + pageSize else pageStart - 1)

    /** Splits items into pages. There's always a first page, since it also holds the widgets. */
    fun <T> pages(items: List<T>, firstPageSlots: Int, otherPageSlots: Int): List<List<T>> {
        val first = items.take(firstPageSlots)
        return listOf(first) + items.drop(first.size).chunked(otherPageSlots.coerceAtLeast(1))
    }

    /** The page slot under a point, clamped to the slots that are in use. */
    fun slotAt(x: Float, y: Float, cellWidth: Float, cellHeight: Float, columns: Int, used: Int): Int {
        val column = (x / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = (y / cellHeight).toInt().coerceAtLeast(0)
        return (row * columns + column).coerceIn(0, (used - 1).coerceAtLeast(0))
    }
}
