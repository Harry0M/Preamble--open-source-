package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free friend-removal impact partitioning for the collaborative-tasks feature.
 *
 * Feature: collaborative-tasks. Before any friend relationship record is deleted, the system must
 * determine the set of collaborative tasks shared with the friend being removed, partitioned into
 * tasks the current user administers (current user is admin) and tasks in which the current user is
 * a non-admin member (Requirement 5.1).
 *
 * This logic is intentionally free of Android, Firestore, and serialization dependencies so it can
 * be validated with fast JVM/property-based tests (Property 13). Callers project their concrete
 * task representation through the [adminUid] and [memberUids] extractors.
 *
 * Partition guarantees (Property 13), for the set of tasks shared with the friend:
 *  - `administeredTasks` are exactly the shared tasks whose admin is the current user.
 *  - `memberTasks` are exactly the shared tasks whose admin is **not** the current user.
 *  - The two sets are disjoint, and their union equals the full shared set.
 */
object FriendRemovalImpactLogic {

    /**
     * The partition of collaborative tasks shared with a friend, split by the current user's role.
     *
     * @property administeredTasks shared tasks for which the current user is the admin.
     * @property memberTasks shared tasks for which the current user is a non-admin member.
     */
    data class Partition<T>(
        val administeredTasks: List<T> = emptyList(),
        val memberTasks: List<T> = emptyList()
    ) {
        /** Total number of shared tasks affected by removing the friend. */
        val totalTasks: Int get() = administeredTasks.size + memberTasks.size

        /** Whether any shared task requires a lifecycle decision before the friend can be removed. */
        val requiresResolution: Boolean get() = totalTasks > 0
    }

    /**
     * Determines whether [tasks] is shared with [friendUid]: the friend is shared on a task when the
     * friend is one of that task's members (the admin is always a member, so this covers both the
     * "friend is admin" and "friend is assignee" cases).
     */
    fun <T> isSharedWith(friendUid: String, task: T, memberUids: (T) -> Set<String>): Boolean =
        friendUid in memberUids(task)

    /**
     * Partitions the tasks shared with [friendUid] into admin-owned and member tasks, relative to
     * [currentUid] (Requirement 5.1).
     *
     * Only tasks shared with the friend are considered; each shared task is placed in exactly one
     * bucket based on whether [currentUid] is its admin, guaranteeing the two buckets are disjoint
     * and together equal the full shared set (Property 13).
     *
     * @param currentUid the signed-in user's uid.
     * @param friendUid the uid of the friend being removed.
     * @param tasks all collaborative tasks visible to the current user.
     * @param adminUid extracts a task's admin uid (or null for a non-collaborative task).
     * @param memberUids extracts a task's full member-uid set (admin included).
     */
    fun <T> partition(
        currentUid: String,
        friendUid: String,
        tasks: List<T>,
        adminUid: (T) -> String?,
        memberUids: (T) -> Set<String>
    ): Partition<T> {
        val shared = tasks.filter { isSharedWith(friendUid, it, memberUids) }
        return Partition(
            administeredTasks = shared.filter { adminUid(it) == currentUid },
            memberTasks = shared.filter { adminUid(it) != currentUid }
        )
    }
}
