package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free preview/overflow logic for the collaborative-tasks feature.
 *
 * Feature: collaborative-tasks. This object is the single source of truth for how the displayed
 * collaborator list is reduced for the two presentation surfaces that show a bounded preview of
 * members:
 *
 *  - the collapsible collaborator list in the Task_Detail_Sheet (Requirement 21), and
 *  - the compact avatar cluster on a Home_Task_List task row (Requirements 22.2, 22.3).
 *
 * The decision is captured by [preview], which takes the already-filtered list of *displayed*
 * members (callers filter terminal statuses through
 * [CollaboratorView.visibleMembers] beforehand) and the current expansion state, and returns the
 * subset to render plus the overflow count and whether an expand/collapse control is warranted.
 *
 * Rules ([PREVIEW_COUNT] = 3):
 *  - `size <= PREVIEW_COUNT`        -> show the full list, `overflow = 0`, `canExpand = false`
 *    (Requirement 21.4; avatar cluster shows all with no "+N").
 *  - `size >  PREVIEW_COUNT` &&      -> show the first [PREVIEW_COUNT], `overflow = size - 3`,
 *    `expanded == false` (collapsed)    `canExpand = true` (Requirements 21.1, 22.2, 22.3).
 *  - `size >  PREVIEW_COUNT` &&      -> show all members, `overflow = 0`, `canExpand = true`
 *    `expanded == true`  (expanded)     (Requirement 21.2). Collapsing returns to the rule above
 *                                       (Requirement 21.3).
 *
 * The function is intentionally generic and free of Android/Compose dependencies so it can be
 * validated with fast JVM/property-based tests (Property 20). The avatar cluster derives its
 * `min(size, 3)` shown / `max(0, size - 3)` overflow counts from the same collapsed call
 * (`preview(list, expanded = false)`), keeping both surfaces consistent.
 */
object CollaboratorPreview {

    /** Maximum number of members shown in the collapsed preview (Member_Preview_Count). */
    const val PREVIEW_COUNT: Int = 3

    /**
     * The result of reducing a displayed-member list to a bounded preview.
     *
     * @param shown the members to render (the full list, the first [PREVIEW_COUNT], or all members).
     * @param overflow the count of displayed members not present in [shown]; always `>= 0`.
     * @param canExpand whether an expand/collapse control should be offered for this list.
     */
    data class Preview<T>(
        val shown: List<T>,
        val overflow: Int,
        val canExpand: Boolean,
    )

    /**
     * Reduces [visible] (the already-filtered list of displayed members) to a [Preview] according
     * to the rules documented on [CollaboratorPreview].
     *
     * @param visible the displayed members, in render order.
     * @param expanded whether the surface is currently expanded; ignored when
     *   `visible.size <= PREVIEW_COUNT`.
     */
    fun <T> preview(visible: List<T>, expanded: Boolean): Preview<T> = when {
        visible.size <= PREVIEW_COUNT ->
            Preview(shown = visible, overflow = 0, canExpand = false)
        expanded ->
            Preview(shown = visible, overflow = 0, canExpand = true)
        else ->
            Preview(
                shown = visible.take(PREVIEW_COUNT),
                overflow = visible.size - PREVIEW_COUNT,
                canExpand = true,
            )
    }
}
