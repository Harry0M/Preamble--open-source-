package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free computation of the visible window for client-side
 * Paged_Loading of the Friends_List and the Friends_Leaderboard
 * (social-hub-redesign, Requirements 2.7, 2.8).
 *
 * The full lightweight list is synced into memory once; rather than rendering
 * the entire list into the `LazyColumn`, only a fixed-size prefix window is
 * emitted and the window grows by one page as the user scrolls toward the end
 * of the loaded entries. This bounds composition size (Requirement 2.3) while
 * keeping the whole set resident for search (Requirement 9.6).
 *
 * This object is free of Android and Firebase dependencies so the windowing
 * logic can be exercised directly by JVM/property-based tests.
 */
object PageWindow {

    /** The number of entries loaded per page. */
    const val PAGE_SIZE: Int = 30

    /**
     * Returns the prefix of [all] that has been "loaded" after [pageCount]
     * pages: the first `min(pageCount * pageSize, all.size)` entries
     * (Requirements 2.7, 2.8).
     *
     * The result is always a prefix of [all] and never longer than [all]; its
     * length is non-decreasing in [pageCount]; once [pageCount] is large enough
     * the whole list is returned. A non-positive [pageCount] yields an empty
     * window.
     *
     * @param all the full, in-memory ordered list.
     * @param pageCount the number of pages loaded so far.
     * @param pageSize the number of entries per page; defaults to [PAGE_SIZE].
     */
    fun <T> visible(all: List<T>, pageCount: Int, pageSize: Int = PAGE_SIZE): List<T> {
        if (pageCount <= 0) return emptyList()
        val end = (pageCount.toLong() * pageSize.toLong())
            .coerceAtMost(all.size.toLong())
            .toInt()
        return all.subList(0, end)
    }

    /**
     * Returns `true` when the user has scrolled close enough to the end of the
     * loaded window to load the next page, i.e. when the last visible index is
     * within [threshold] of the last loaded entry (Requirements 2.7, 2.8).
     *
     * @param lastVisibleIndex the index of the last entry currently visible in
     *   the list.
     * @param loadedCount the number of entries currently loaded into the window.
     * @param threshold how close to the end the user must scroll to trigger the
     *   next page; defaults to 5.
     */
    fun shouldLoadMore(lastVisibleIndex: Int, loadedCount: Int, threshold: Int = 5): Boolean =
        lastVisibleIndex >= loadedCount - threshold
}
