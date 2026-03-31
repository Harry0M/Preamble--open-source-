# Firestore Architecture Notes

This project now runs with:
- Room as the on-device source of truth.
- Cloud Firestore as the cloud sync backend.

RTDB sync has been removed from runtime code.

## Live Data Model

Firestore database id:
- `preamble`

Collections:
- `users/{uid}/tasks/{taskId}`
- `users/{uid}/tagOverrides/{googleId}`

## Sync Behavior

1. All writes happen local-first (Room).
2. Sync manager mirrors local-origin tasks to Firestore.
3. Firestore snapshot listeners merge remote updates into Room.
4. Google Calendar / Google Tasks tag overrides are persisted in:
- Room (`task_tag_overrides`)
- Firestore (`users/{uid}/tagOverrides`)

## Feature Scope Preserved

Unchanged behavior:
- Local tasks + completion + editing + deletion
- Recurrence and subtasks
- Google Calendar / Google Tasks sync flows
- Tag override persistence across devices
- Offline-first UX with eventual cloud sync

## Rules

Firestore rules file:
- `firebase-firestore-rules.rules`

Rules are owner-scoped:
- A user can only read/write under `users/{their_uid}/...`
- Task and tag override writes are validated by type/shape guards

## Verification Checklist

1. Sign in with a test account.
2. Create/edit/delete local tasks and verify Firestore docs update.
3. Apply tags to Google-sourced items and verify `tagOverrides` writes.
4. Run `Settings -> Validate Firestore Mirror`.
5. Confirm parity logs under `FirebaseTaskSync`.

