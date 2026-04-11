const express = require('express');
const session = require('express-session');
const cookieParser = require('cookie-parser');
const path = require('path');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: 'https://preambl-fbea6-default-rtdb.firebaseio.com'
});

// Use the "preamble" database (not default)
const { Firestore } = require('@google-cloud/firestore');
const firestoreDb = new Firestore({
  projectId: 'preambl-fbea6',
  credentials: {
    client_email: serviceAccount.client_email,
    private_key: serviceAccount.private_key
  },
  databaseId: 'preamble'
});

const ADMIN_EMAIL = 'palhariom698@gmail.com';
const PORT = process.env.PORT || 3000;

const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());
app.use(session({
  secret: 'preamble-admin-secret-key-2026',
  resave: false,
  saveUninitialized: false,
  cookie: { secure: false, maxAge: 24 * 60 * 60 * 1000 }
}));
app.use(express.static(path.join(__dirname, 'public')));

// Auth middleware
function requireAuth(req, res, next) {
  if (req.session && req.session.user && req.session.user.email === ADMIN_EMAIL) {
    return next();
  }
  if (req.xhr || req.headers.accept?.includes('application/json')) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  return res.redirect('/');
}

// ─── Auth Routes ───

app.post('/api/auth/login', async (req, res) => {
  try {
    const { idToken } = req.body;
    if (!idToken) return res.status(400).json({ error: 'Missing ID token' });

    const decoded = await admin.auth().verifyIdToken(idToken);
    if (decoded.email !== ADMIN_EMAIL) {
      return res.status(403).json({ error: 'Access denied. Only admin can login.' });
    }

    req.session.user = {
      uid: decoded.uid,
      email: decoded.email,
      name: decoded.name || decoded.email,
      picture: decoded.picture || null
    };

    res.json({ success: true, user: req.session.user });
  } catch (err) {
    console.error('Login error:', err);
    res.status(401).json({ error: 'Invalid token' });
  }
});

app.post('/api/auth/logout', (req, res) => {
  req.session.destroy();
  res.json({ success: true });
});

app.get('/api/auth/me', (req, res) => {
  if (req.session && req.session.user) {
    return res.json({ user: req.session.user });
  }
  res.status(401).json({ error: 'Not authenticated' });
});

// ─── Users Routes ───

app.get('/api/users', requireAuth, async (req, res) => {
  try {
    const usersSnap = await firestoreDb.collection('users').get();
    const users = [];
    for (const doc of usersSnap.docs) {
      const data = doc.data();
      // Count tasks for this user
      const tasksSnap = await firestoreDb.collection('tasks').where('uid', '==', doc.id).get();
      users.push({
        uid: doc.id,
        email: data.email || 'N/A',
        lastSeenAt: data.lastSeenAt || null,
        updatedTimestamp: data.updatedTimestamp || null,
        blocked: data.blocked || false,
        gender: data.gender || null,
        age: data.age || null,
        taskCount: tasksSnap.size
      });
    }
    res.json({ users });
  } catch (err) {
    console.error('Error fetching users:', err);
    res.status(500).json({ error: 'Failed to fetch users' });
  }
});

app.get('/api/users/:uid', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const userDoc = await firestoreDb.collection('users').doc(uid).get();
    if (!userDoc.exists) {
      return res.status(404).json({ error: 'User not found' });
    }
    const userData = userDoc.data();

    // Get Firebase Auth user info
    let authUser = null;
    try {
      authUser = await admin.auth().getUser(uid);
    } catch (e) { /* user might not exist in auth */ }

    // Get all tasks
    const tasksSnap = await firestoreDb.collection('tasks').where('uid', '==', uid).get();
    const tasks = tasksSnap.docs.map(doc => {
      const data = doc.data();
      return { docId: doc.id, ...data };
    });

    // Get tag overrides
    const tagSnap = await firestoreDb.collection('tagOverrides').where('uid', '==', uid).get();
    const tagOverrides = tagSnap.docs.map(doc => ({ docId: doc.id, ...doc.data() }));

    res.json({
      user: {
        uid,
        email: userData.email || authUser?.email || 'N/A',
        displayName: authUser?.displayName || userData.displayName || 'N/A',
        photoURL: authUser?.photoURL || null,
        lastSeenAt: userData.lastSeenAt || null,
        updatedTimestamp: userData.updatedTimestamp || null,
        blocked: userData.blocked || false,
        gender: userData.gender || null,
        age: userData.age || null,
        disabled: authUser?.disabled || false,
        creationTime: authUser?.metadata?.creationTime || null,
        lastSignInTime: authUser?.metadata?.lastSignInTime || null
      },
      tasks,
      tagOverrides
    });
  } catch (err) {
    console.error('Error fetching user:', err);
    res.status(500).json({ error: 'Failed to fetch user details' });
  }
});

// Block / Unblock user
app.post('/api/users/:uid/block', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const { blocked } = req.body;

    // Update Firestore user doc
    await firestoreDb.collection('users').doc(uid).update({ blocked: !!blocked });

    // Disable/enable in Firebase Auth
    await admin.auth().updateUser(uid, { disabled: !!blocked });

    res.json({ success: true, blocked: !!blocked });
  } catch (err) {
    console.error('Error blocking user:', err);
    res.status(500).json({ error: 'Failed to update user status' });
  }
});

// ─── Tasks Routes ───

// Helper: encode/decode Firestore doc IDs (same as Android app)
function encodeDocId(raw) { return raw.replace(/\//g, '%2F'); }
function decodeDocId(encoded) { return encoded.replace(/%2F/g, '/'); }

// Get single task
app.get('/api/tasks/:docId', requireAuth, async (req, res) => {
  try {
    const doc = await firestoreDb.collection('tasks').doc(req.params.docId).get();
    if (!doc.exists) return res.status(404).json({ error: 'Task not found' });
    res.json({ docId: doc.id, ...doc.data() });
  } catch (err) {
    console.error('Error fetching task:', err);
    res.status(500).json({ error: 'Failed to fetch task' });
  }
});

// Create task for a user
app.post('/api/users/:uid/tasks', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const taskId = req.body.id || generateId();
    const now = Date.now();
    const today = new Date().toISOString().split('T')[0];

    const task = {
      uid,
      id: taskId,
      title: req.body.title || 'Untitled Task',
      isCompleted: req.body.isCompleted || false,
      createdDate: req.body.createdDate || today,
      createdTimestamp: req.body.createdTimestamp || now,
      completedTimestamp: req.body.completedTimestamp || null,
      deadlineTime: req.body.deadlineTime || null,
      updatedTimestamp: now,
      source: req.body.source || 'local',
      priority: req.body.priority || 0,
      description: req.body.description || null,
      recurrenceType: req.body.recurrenceType || null,
      recurrenceInterval: req.body.recurrenceInterval || null,
      recurrenceDays: req.body.recurrenceDays || null,
      recurrenceEndDate: req.body.recurrenceEndDate || null,
      recurrenceParentId: req.body.recurrenceParentId || null,
      parentTaskId: req.body.parentTaskId || null,
      tags: req.body.tags || null,
      googleCalendarId: req.body.googleCalendarId || null,
      googleRecurrenceInfo: req.body.googleRecurrenceInfo || null
    };

    const docId = encodeDocId(`${uid}::${taskId}`);
    await firestoreDb.collection('tasks').doc(docId).set(task);
    res.json({ success: true, docId, task });
  } catch (err) {
    console.error('Error creating task:', err);
    res.status(500).json({ error: 'Failed to create task' });
  }
});

// Update task
app.put('/api/tasks/:docId', requireAuth, async (req, res) => {
  try {
    const { docId } = req.params;
    const updates = { ...req.body, updatedTimestamp: Date.now() };
    delete updates.docId; // don't store docId as field

    await firestoreDb.collection('tasks').doc(docId).update(updates);
    res.json({ success: true });
  } catch (err) {
    console.error('Error updating task:', err);
    res.status(500).json({ error: 'Failed to update task' });
  }
});

// Delete task
app.delete('/api/tasks/:docId', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('tasks').doc(req.params.docId).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting task:', err);
    res.status(500).json({ error: 'Failed to delete task' });
  }
});

// ─── Dashboard Stats ───

app.get('/api/stats', requireAuth, async (req, res) => {
  try {
    const usersSnap = await firestoreDb.collection('users').get();
    const tasksSnap = await firestoreDb.collection('tasks').get();

    let completedTasks = 0;
    let activeTasks = 0;
    let blockedUsers = 0;

    tasksSnap.docs.forEach(doc => {
      const data = doc.data();
      if (data.isCompleted) completedTasks++;
      else activeTasks++;
    });

    usersSnap.docs.forEach(doc => {
      if (doc.data().blocked) blockedUsers++;
    });

    res.json({
      totalUsers: usersSnap.size,
      totalTasks: tasksSnap.size,
      activeTasks,
      completedTasks,
      blockedUsers
    });
  } catch (err) {
    console.error('Error fetching stats:', err);
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

// ─── Broadcasts (Admin Tasks) Routes ───

// List all broadcasts
app.get('/api/broadcasts', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('broadcasts').orderBy('createdAt', 'desc').get();
    const broadcasts = snap.docs.map(doc => {
      const data = doc.data();
      // Ensure tags is always an array (may be stored as JSON string from older creates)
      if (typeof data.tags === 'string') {
        try { data.tags = JSON.parse(data.tags); } catch(e) { data.tags = []; }
      }
      if (!Array.isArray(data.tags)) data.tags = [];
      return { id: doc.id, ...data };
    });
    res.json({ broadcasts });
  } catch (err) {
    console.error('Error fetching broadcasts:', err);
    res.status(500).json({ error: 'Failed to fetch broadcasts' });
  }
});

// Create broadcast (+ auto-send push notification)
app.post('/api/broadcasts', requireAuth, async (req, res) => {
  try {
    const id = generateId();
    const now = Date.now();

    // Parse tags from JSON string or array
    let tags = [];
    if (req.body.tags) {
      try {
        tags = typeof req.body.tags === 'string' ? JSON.parse(req.body.tags) : req.body.tags;
      } catch (e) { tags = []; }
    }

    const broadcast = {
      title: req.body.title || 'Untitled',
      description: req.body.description || null,
      imageUrl: req.body.imageUrl || null,
      actionUrl: req.body.actionUrl || null,
      deepLink: req.body.deepLink || null,
      actionLabel: req.body.actionLabel || 'Open Now',
      type: req.body.type || 'announcement',
      tags: tags,
      directRedirect: req.body.directRedirect || false,
      priority: parseInt(req.body.priority) || 0,
      createdAt: now,
      expiresAt: req.body.expiresAt ? parseInt(req.body.expiresAt) : null,
      active: req.body.active !== false,
      featureKey: req.body.featureKey || null,
      dismissible: req.body.dismissible !== false,
      targetType: req.body.targetType || 'all',    // all, group, single
      targetGroupId: req.body.targetGroupId || null,
      targetUids: req.body.targetUids || null
    };

    await firestoreDb.collection('broadcasts').doc(id).set(broadcast);

    // Auto-send push notification if broadcast is active and notify is enabled
    let notifySent = 0;
    if (broadcast.active && req.body.autoNotify !== false) {
      const dataPayload = {
        title: broadcast.title,
        body: broadcast.description || broadcast.title,
        channelType: 'broadcast',
        deepLink: broadcast.deepLink || 'preamble://home'  // default to home so app navigates
      };

      try {
        if (broadcast.targetType === 'group' && broadcast.targetGroupId) {
          const groupDoc = await firestoreDb.collection('user_groups').doc(broadcast.targetGroupId).get();
          if (groupDoc.exists) {
            const uids = await resolveGroupMembers(groupDoc.data());
            notifySent = await sendNotificationToUids(uids, dataPayload);
          }
        } else if (broadcast.targetType === 'single' && broadcast.targetUids) {
          const uids = Array.isArray(broadcast.targetUids) ? broadcast.targetUids : [broadcast.targetUids];
          notifySent = await sendNotificationToUids(uids, dataPayload);
        } else {
          // Send to all
          const usersSnap = await firestoreDb.collection('users').get();
          const tokens = [];
          usersSnap.docs.forEach(doc => {
            const data = doc.data();
            if (data.fcmToken && !data.blocked) tokens.push(data.fcmToken);
          });
          notifySent = await sendToTokens(tokens, dataPayload);
        }

        // Log the auto-notification
        await firestoreDb.collection('notification_log').add({
          title: broadcast.title,
          body: broadcast.description || broadcast.title,
          deepLink: broadcast.deepLink || null,
          channelType: 'broadcast',
          targetType: broadcast.targetType || 'all',
          targetGroupId: broadcast.targetGroupId || null,
          sentAt: Date.now(),
          sentCount: notifySent,
          sentBy: req.session.user.email,
          source: 'broadcast_auto'
        });
      } catch (notifyErr) {
        console.error('Auto-notify failed (broadcast still created):', notifyErr);
      }
    }

    res.json({ success: true, id, broadcast, notifySent });
  } catch (err) {
    console.error('Error creating broadcast:', err);
    res.status(500).json({ error: 'Failed to create broadcast' });
  }
});

// Update broadcast
app.put('/api/broadcasts/:id', requireAuth, async (req, res) => {
  try {
    const updates = { ...req.body };
    delete updates.id;

    // Parse tags if provided
    if (updates.tags && typeof updates.tags === 'string') {
      try { updates.tags = JSON.parse(updates.tags); } catch (e) { delete updates.tags; }
    }
    if (updates.priority) updates.priority = parseInt(updates.priority);
    if (updates.expiresAt) updates.expiresAt = parseInt(updates.expiresAt);

    await firestoreDb.collection('broadcasts').doc(req.params.id).update(updates);
    res.json({ success: true });
  } catch (err) {
    console.error('Error updating broadcast:', err);
    res.status(500).json({ error: 'Failed to update broadcast' });
  }
});

// Delete broadcast
app.delete('/api/broadcasts/:id', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('broadcasts').doc(req.params.id).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting broadcast:', err);
    res.status(500).json({ error: 'Failed to delete broadcast' });
  }
});

// Toggle broadcast active status
app.post('/api/broadcasts/:id/toggle', requireAuth, async (req, res) => {
  try {
    const doc = await firestoreDb.collection('broadcasts').doc(req.params.id).get();
    if (!doc.exists) return res.status(404).json({ error: 'Not found' });
    const currentActive = doc.data().active !== false;
    await firestoreDb.collection('broadcasts').doc(req.params.id).update({ active: !currentActive });
    res.json({ success: true, active: !currentActive });
  } catch (err) {
    console.error('Error toggling broadcast:', err);
    res.status(500).json({ error: 'Failed to toggle broadcast' });
  }
});

// ─── User Groups Routes ───

// List all groups
app.get('/api/groups', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('user_groups').orderBy('createdAt', 'desc').get();
    const groups = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    res.json({ groups });
  } catch (err) {
    console.error('Error fetching groups:', err);
    res.status(500).json({ error: 'Failed to fetch groups' });
  }
});

// Create group
app.post('/api/groups', requireAuth, async (req, res) => {
  try {
    const id = generateId();
    const group = {
      name: req.body.name || 'Untitled Group',
      description: req.body.description || null,
      filterType: req.body.filterType || 'manual',  // manual, gender, age, auto
      filterGender: req.body.filterGender || null,   // male, female, other
      filterAgeMin: req.body.filterAgeMin ? parseInt(req.body.filterAgeMin) : null,
      filterAgeMax: req.body.filterAgeMax ? parseInt(req.body.filterAgeMax) : null,
      manualUids: req.body.manualUids || [],          // manually added user UIDs
      createdAt: Date.now()
    };

    await firestoreDb.collection('user_groups').doc(id).set(group);
    res.json({ success: true, id, group });
  } catch (err) {
    console.error('Error creating group:', err);
    res.status(500).json({ error: 'Failed to create group' });
  }
});

// Update group
app.put('/api/groups/:id', requireAuth, async (req, res) => {
  try {
    const updates = { ...req.body };
    delete updates.id;
    if (updates.filterAgeMin) updates.filterAgeMin = parseInt(updates.filterAgeMin);
    if (updates.filterAgeMax) updates.filterAgeMax = parseInt(updates.filterAgeMax);

    await firestoreDb.collection('user_groups').doc(req.params.id).update(updates);
    res.json({ success: true });
  } catch (err) {
    console.error('Error updating group:', err);
    res.status(500).json({ error: 'Failed to update group' });
  }
});

// Delete group
app.delete('/api/groups/:id', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('user_groups').doc(req.params.id).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting group:', err);
    res.status(500).json({ error: 'Failed to delete group' });
  }
});

// Resolve group members — returns list of UIDs matching the group's filters
app.get('/api/groups/:id/members', requireAuth, async (req, res) => {
  try {
    const groupDoc = await firestoreDb.collection('user_groups').doc(req.params.id).get();
    if (!groupDoc.exists) return res.status(404).json({ error: 'Group not found' });

    const group = groupDoc.data();
    const uids = await resolveGroupMembers(group);
    res.json({ count: uids.length, uids });
  } catch (err) {
    console.error('Error resolving group members:', err);
    res.status(500).json({ error: 'Failed to resolve members' });
  }
});

// Helper: resolve group member UIDs based on filters
async function resolveGroupMembers(group) {
  if (group.filterType === 'manual') {
    return group.manualUids || [];
  }

  const usersSnap = await firestoreDb.collection('users').get();
  const uids = [];

  for (const doc of usersSnap.docs) {
    const data = doc.data();
    if (data.blocked) continue;

    let match = true;

    if (group.filterGender && data.gender) {
      if (data.gender !== group.filterGender) match = false;
    } else if (group.filterGender && !data.gender) {
      match = false; // user has no gender data, skip
    }

    if (match && group.filterAgeMin != null && data.age != null) {
      if (data.age < group.filterAgeMin) match = false;
    }
    if (match && group.filterAgeMax != null && data.age != null) {
      if (data.age > group.filterAgeMax) match = false;
    }
    if (match && (group.filterAgeMin != null || group.filterAgeMax != null) && data.age == null) {
      match = false; // no age data, can't filter
    }

    if (match) uids.push(doc.id);
  }

  return uids;
}

// Helper: send FCM notification to a list of UIDs (deduplicates)
async function sendNotificationToUids(uids, dataPayload) {
  const uniqueUids = [...new Set(uids)];
  const tokens = [];
  for (const uid of uniqueUids) {
    const userDoc = await firestoreDb.collection('users').doc(uid).get();
    if (userDoc.exists) {
      const token = userDoc.data().fcmToken;
      if (token && !userDoc.data().blocked) tokens.push(token);
    }
  }
  return await sendToTokens(tokens, dataPayload);
}

// Helper: send FCM to token list in batches (deduplicates tokens)
async function sendToTokens(tokens, dataPayload) {
  tokens = [...new Set(tokens)]; // Remove duplicate tokens
  if (tokens.length === 0) return 0;
  let sent = 0;
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const response = await admin.messaging().sendEachForMulticast({
      tokens: batch,
      data: dataPayload,
      android: { priority: 'high' }
    });
    sent += response.successCount;
  }
  return sent;
}

// ─── Push Notifications Routes ───

// Send notification to users (all, single, or group)
app.post('/api/notifications/send', requireAuth, async (req, res) => {
  try {
    const { title, body, deepLink, url, channelType, targetType, targetUid, targetGroupId } = req.body;

    if (!title || !body) {
      return res.status(400).json({ error: 'Title and body are required' });
    }

    const dataPayload = {
      title,
      body,
      channelType: channelType || 'broadcast'
    };
    if (deepLink) dataPayload.deepLink = deepLink;
    if (url) dataPayload.url = url;

    let sent = 0;

    if (targetType === 'single' && targetUid) {
      // Send to specific user via their FCM token
      const userDoc = await firestoreDb.collection('users').doc(targetUid).get();
      const token = userDoc.exists ? userDoc.data().fcmToken : null;
      if (!token) {
        return res.status(400).json({ error: 'User has no FCM token' });
      }
      await admin.messaging().send({
        token,
        data: dataPayload,
        android: { priority: 'high' }
      });
      sent = 1;
    } else if (targetType === 'group' && targetGroupId) {
      // Send to a user group
      const groupDoc = await firestoreDb.collection('user_groups').doc(targetGroupId).get();
      if (!groupDoc.exists) {
        return res.status(400).json({ error: 'Group not found' });
      }
      const uids = await resolveGroupMembers(groupDoc.data());
      sent = await sendNotificationToUids(uids, dataPayload);
    } else {
      // Send to all users who have FCM tokens
      const usersSnap = await firestoreDb.collection('users').get();
      const tokens = [];
      usersSnap.docs.forEach(doc => {
        const data = doc.data();
        if (data.fcmToken && !data.blocked) {
          tokens.push(data.fcmToken);
        }
      });
      sent = await sendToTokens(tokens, dataPayload);
    }

    // Log the notification
    await firestoreDb.collection('notification_log').add({
      title,
      body,
      deepLink: deepLink || null,
      url: url || null,
      channelType: channelType || 'broadcast',
      targetType: targetType || 'all',
      targetUid: targetUid || null,
      targetGroupId: targetGroupId || null,
      sentAt: Date.now(),
      sentCount: sent,
      sentBy: req.session.user.email
    });

    res.json({ success: true, sent });
  } catch (err) {
    console.error('Error sending notification:', err);
    res.status(500).json({ error: 'Failed to send notification: ' + err.message });
  }
});

// Get notification history
app.get('/api/notifications/history', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('notification_log')
      .orderBy('sentAt', 'desc')
      .limit(50)
      .get();
    const history = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    res.json({ history });
  } catch (err) {
    console.error('Error fetching notification history:', err);
    res.status(500).json({ error: 'Failed to fetch history' });
  }
});

// ─── Serve Frontend ───

app.get('/dashboard', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'dashboard.html'));
});

app.get('/users', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'users.html'));
});

app.get('/users/:uid', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'user-detail.html'));
});

app.get('/broadcasts', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'broadcasts.html'));
});

app.get('/notifications', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'notifications.html'));
});

app.get('/groups', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'groups.html'));
});

app.get('/', (req, res) => {
  if (req.session && req.session.user) {
    return res.redirect('/dashboard');
  }
  res.sendFile(path.join(__dirname, 'public', 'login.html'));
});

// Simple ID generator
function generateId() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let id = '';
  for (let i = 0; i < 20; i++) {
    id += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return id;
}

app.listen(PORT, () => {
  console.log(`\n  ✦ Preamble Admin Panel running at http://localhost:${PORT}\n`);
});
