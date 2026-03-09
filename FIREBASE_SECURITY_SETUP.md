# Firebase Realtime Database Security Setup

## ⚠️ CRITICAL: Apply Security Rules IMMEDIATELY

Your app writes to Firebase Realtime Database at `users/{uid}/tasks`. **Without proper security rules, your database is vulnerable.**

---

## 🔐 How to Apply Security Rules

### Step 1: Open Firebase Console
1. Go to https://console.firebase.google.com
2. Select your project: **preambl-fbea6**

### Step 2: Navigate to Realtime Database Rules
1. In left sidebar, click **"Realtime Database"**
2. Click **"Rules"** tab at top

### Step 3: Replace with Secure Rules
1. Copy the entire content from `firebase-rtdb-rules.json` 
2. Paste into the Rules editor in Firebase Console
3. Click **"Publish"** button

---

## ✅ What These Rules Do

### User Data Isolation
```json
"$uid": {
  ".read": "$uid === auth.uid",
  ".write": "$uid === auth.uid"
}
```
- Each user can **only** read/write their own data at `users/{their_uid}/`
- No user can access another user's tasks
- Unauthenticated users have **zero** access

### Data Validation
- **title**: Required, 1-500 characters
- **createdDate**: Must match `YYYY-MM-DD` format
- **timestamps**: Must be positive numbers
- **isCompleted**: Must be boolean
- Rejects any unknown fields to prevent data pollution

### Default Deny
```json
"$other": {
  ".read": false,
  ".write": false
}
```
Any path not explicitly allowed is blocked.

---

## 🚨 Current Risk (If Not Applied)

**Default Test Rules** in Firebase expire after 30 days and look like:
```json
{
  "rules": {
    ".read": "now < 1234567890000",
    ".write": "now < 1234567890000"
  }
}
```

### Consequences:
- ❌ After expiry: No reads/writes work → **App breaks completely**
- ❌ Before expiry: **Anyone** can read/modify **all** user data
- ❌ No validation: Malformed data can corrupt your database

---

## ✓ Verification

After applying rules, test in Firebase Console:

### Test Read (Should Fail - Good!)
**Simulate**: Unauthenticated read at `users/someUserId/tasks`  
**Expected**: ❌ **Permission Denied**

### Test Write (Should Fail - Good!)
**Simulate**: Unauthenticated write at `users/someUserId/tasks`  
**Expected**: ❌ **Permission Denied**

### Test Authenticated User (Should Succeed)
**Simulate**: Authenticated as `uid: testUser123`, read at `users/testUser123/tasks`  
**Expected**: ✅ **Read Allowed**

---

## 📋 Quick Verification Checklist

- [ ] Firebase Console → Realtime Database → Rules tab opened
- [ ] `firebase-rtdb-rules.json` content copied
- [ ] Rules pasted and **Published** in Console
- [ ] Test simulator shows **Permission Denied** for unauthorized access
- [ ] App still syncs tasks when signed in (check Logcat for `FirebaseTaskSync` logs)

---

## 🛠 Troubleshooting

### "Permission Denied" error in app after applying rules
**Cause**: User not signed in or auth token expired  
**Fix**: Check `Logcat` for `"Skipping pushTask/deleteTask because no authenticated user"`

### Tasks not syncing after sign-in
**Check**: 
1. Logcat filter: `FirebaseTaskSync`
2. Look for `"Realtime listener cancelled"` with `PERMISSION_DENIED` code
3. Verify you're signed in: Settings → check Google account name

### Need to modify rules later
**Safe Changes**:
- ✅ Increase title max length (change `500` to higher number)
- ✅ Add new optional fields (mark with `!newData.exists()`)

**Dangerous Changes**:
- ❌ Removing `$uid === auth.uid` check
- ❌ Allowing `.read: true` or `.write: true` globally
- ❌ Removing data validation rules

---

## 🔍 Current Database URL

Your app uses: `https://preambl-fbea6-default-rtdb.firebaseio.com`

Data structure:
```
users/
  {uid}/
    tasks/
      {taskId}/
        id: "..."
        title: "..."
        isCompleted: true/false
        createdDate: "2026-03-08"
        createdTimestamp: 1234567890
        updatedTimestamp: 1234567890
        completedTimestamp: 1234567890 (optional)
        deadlineTime: "14:30" (optional)
```

---

**Apply these rules NOW to secure your user data! 🔒**
