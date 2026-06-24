package com.theblankstate.preamble.collab

/**
 * Pure selection logic for a Member_Avatar image with a Default_Avatar fallback
 * (Requirement 26).
 *
 * The Home_Task_List shows each collaborator inside an Expressive_Member_Shape.
 * Req 26 layers a real Google_Profile_Image on top of that shape with a strict
 * precedence: a genuine photograph wins, and in every other situation — no URL,
 * a Generated_Initials_Avatar / monogram placeholder, a failed fetch, or while
 * the image is still loading — the bundled Default_Avatar is shown instead.
 *
 * This object is deliberately free of Android, Coil, and Firestore dependencies
 * so the precedence ([select]) and the placeholder heuristic
 * ([isGeneratedInitialsAvatar]) can be exercised directly by JVM
 * property-/edge-case tests (Property 22). The `MemberAvatar` composable adapts
 * this decision to Coil and the bundled drawable.
 */
object AvatarSource {

    /** Which image the UI should render for a member. */
    enum class Source {
        /** A genuine Google_Profile_Image is available and should be shown. */
        REAL_PHOTO,

        /** The bundled Default_Avatar should be shown (Req 26.2, 26.3, 26.4, 26.5). */
        DEFAULT
    }

    /**
     * Applies the Req 26.4 precedence.
     *
     * A real Google photo ([Source.REAL_PHOTO]) is chosen only when all three
     * hold: a photo URL is available ([hasRealPhoto]), that URL is not a
     * generated initials/monogram placeholder ([isInitialsPlaceholder]), and the
     * fetch has not failed ([fetchFailed]). Every other combination — including
     * "no url", "placeholder", "fetch failed", and "still loading" — resolves to
     * [Source.DEFAULT].
     */
    fun select(hasRealPhoto: Boolean, isInitialsPlaceholder: Boolean, fetchFailed: Boolean): Source =
        if (hasRealPhoto && !isInitialsPlaceholder && !fetchFailed) Source.REAL_PHOTO else Source.DEFAULT

    // --- isGeneratedInitialsAvatar heuristic patterns ---------------------------------
    //
    // Google serves account avatars from the lh3.googleusercontent.com (and the
    // regional lhN.* / *.googleusercontent.com) hosts. A genuine uploaded photo
    // lives under an `/a/<opaque-id>` path, whereas Google's auto-generated
    // monogram (the coloured circle with the user's initial, returned when the
    // account has no real photo) is served from a small family of distinguishable
    // URL shapes. We treat the following as placeholders:
    //
    //   1. `/a/default-user`        — the explicit anonymous default-avatar path.
    //   2. `/a-/`                    — the legacy monogram path segment Google uses
    //                                  for generated (non-uploaded) avatars, as
    //                                  opposed to `/a/` for real photos.
    //   3. a `-mo` monogram marker in the size/option suffix, e.g. `=s96-mo` or
    //      `...-c-mo`. Google appends `-mo` ("monogram") to the sizing options of
    //      a generated initials avatar; real photos never carry it.
    //
    // Matching is done case-insensitively against the trimmed URL.

    /** Explicit anonymous default-avatar path Google serves when no photo exists. */
    private const val DEFAULT_USER_PATH = "/a/default-user"

    /** Legacy monogram path segment (generated avatars use `/a-/`, real photos use `/a/`). */
    private const val MONOGRAM_PATH_SEGMENT = "/a-/"

    /**
     * The `-mo` ("monogram") sizing-option marker Google appends to generated
     * initials avatars. It appears in the `=...` options suffix, either as the
     * whole option (`-mo`) at the end or followed by a further option separator.
     */
    private val MONOGRAM_SIZING_SUFFIX = Regex("=[^/?#]*-mo(?:[^A-Za-z0-9].*)?$", RegexOption.IGNORE_CASE)

    /**
     * Heuristic detector for a Generated_Initials_Avatar / Google default
     * monogram URL (Req 26.3).
     *
     * Returns `true` when [url] does not denote a usable real photograph:
     *  - `null` or blank → `true` (there is no real photo to show), and
     *  - a Google default-avatar / monogram URL — one whose path contains
     *    `/a/default-user`, uses the `/a-/` monogram path segment, or carries the
     *    `=...-mo` monogram sizing suffix → `true`.
     *
     * Returns `false` for a real photo URL such as
     * `https://lh3.googleusercontent.com/a/<opaque-id>=s96-c`, so the precedence
     * in [select] can show it.
     */
    fun isGeneratedInitialsAvatar(url: String?): Boolean {
        // No URL at all means there is no real photo to display.
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return true

        val lower = trimmed.lowercase()
        return lower.contains(DEFAULT_USER_PATH) ||
            lower.contains(MONOGRAM_PATH_SEGMENT) ||
            MONOGRAM_SIZING_SUFFIX.containsMatchIn(trimmed)
    }
}
