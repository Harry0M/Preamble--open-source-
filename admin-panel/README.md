# Preamble Admin Panel

Admin panel for managing Preamble app users and tasks.

## Setup

1. Navigate to the admin-panel directory:
   ```
   cd admin-panel
   ```

2. Install dependencies:
   ```
   npm install
   ```

3. Ensure `serviceAccountKey.json` is present in the admin-panel root (not committed to git).

4. Start the server:
   ```
   npm start
   ```

5. Open http://localhost:3000 in your browser.

## Features

- **Google Login** — Only `palhariom698@gmail.com` can access the panel
- **Dashboard** — Overview stats (total users, tasks, completed, blocked)
- **Users List** — Searchable list of all users with task counts
- **User Detail** — Full user profile with:
  - All tasks with edit/delete/add capabilities
  - User info (Firebase Auth + Firestore data)
  - Tag overrides
  - Block/Unblock user functionality

## Tech Stack

- **Backend:** Express.js + Firebase Admin SDK
- **Frontend:** Plain HTML + Custom CSS (no frameworks)
- **Database:** Firestore (database ID: "preamble")
- **Auth:** Google OAuth via Firebase Auth (client-side) + session-based server auth

## Design

Dark theme with vibrant minimalism aesthetic:
- Inter font family
- Glassmorphism effects
- Color palette: Black (#0a0a0a), White (#fafafa), Accent Indigo (#6366f1)
