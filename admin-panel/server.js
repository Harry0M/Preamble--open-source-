import dotenv from 'dotenv';
dotenv.config();

import express from 'express';
import session from 'express-session';
import cookieParser from 'cookie-parser';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import admin from 'firebase-admin';
import { Firestore } from '@google-cloud/firestore';
import { GoogleGenAI } from '@google/genai';
import NodeCache from 'node-cache';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const cache = new NodeCache({ stdTTL: 300, checkperiod: 60 });

// Load Firebase Service Account in ES Modules
const serviceAccount = JSON.parse(
  fs.readFileSync(new URL('./serviceAccountKey.json', import.meta.url))
);

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: 'https://preambl-fbea6-default-rtdb.firebaseio.com',
  storageBucket: 'preambl-fbea6.firebasestorage.app'
});

// Configure separate "preamble" database in Firestore
const firestoreDb = new Firestore({
  projectId: 'preambl-fbea6',
  credentials: {
    client_email: serviceAccount.client_email,
    private_key: serviceAccount.private_key
  },
  databaseId: 'preamble'
});

// Initialize Gemini (Will use process.env.GEMINI_API_KEY)
let ai = null;
try {
  if (process.env.GEMINI_API_KEY) {
    ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  } else {
    console.warn('Gemini API key not found in environment');
  }
} catch (e) {
  console.warn('Gemini API not initialized:', e.message);
}

const ADMIN_EMAIL = 'palhariom698@gmail.com';
const PORT = process.env.PORT || 3000;

const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());

// Persistent-ready session configuration
const sessionSecret = process.env.SESSION_SECRET || 'preamble-admin-secret-key-2026';
app.use(session({
  secret: sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: { secure: false, maxAge: 24 * 60 * 60 * 1000 } // 24 hours
}));

// Serve static React production bundle from 'dist'
app.use(express.static(path.join(__dirname, 'dist')));

// Middleware to secure admin endpoints
function requireAuth(req, res, next) {
  if (req.session && req.session.user && (req.session.user.role === 'admin' || req.session.user.email === ADMIN_EMAIL)) {
    return next();
  }
  if (req.xhr || req.headers.accept?.includes('application/json')) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  return res.redirect('/');
}

// ─── AUTHENTICATION ROUTES ───

app.post('/api/auth/login', async (req, res) => {
  try {
    const { idToken } = req.body;
    if (!idToken) return res.status(400).json({ error: 'Missing ID token' });

    const decoded = await admin.auth().verifyIdToken(idToken);
    
    // Check if UID exists in Firestore 'admins' collection or is the fail-safe ADMIN_EMAIL
    const adminDoc = await firestoreDb.collection('admins').doc(decoded.uid).get();
    const isSuperAdmin = decoded.email === ADMIN_EMAIL;

    if (!adminDoc.exists && !isSuperAdmin) {
      return res.status(403).json({ error: 'Access denied. You are not registered as an admin.' });
    }

    req.session.user = {
      uid: decoded.uid,
      email: decoded.email,
      name: decoded.name || decoded.email,
      picture: decoded.picture || null,
      role: isSuperAdmin ? 'super_admin' : (adminDoc.data()?.role || 'admin')
    };

    res.json({ success: true, user: req.session.user });
  } catch (err) {
    console.error('Login error:', err);
    res.status(401).json({ error: 'Invalid authentication token' });
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

// ─── USER DIRECTORY ROUTES ───

// Get paginated users
app.get('/api/users', requireAuth, async (req, res) => {
  try {
    const limit = Math.min(parseInt(req.query.limit) || 20, 100);
    const startAfterId = req.query.startAfter;

    let query = firestoreDb.collection('users').orderBy('lastSeenAt', 'desc');

    if (startAfterId) {
      const startAfterDoc = await firestoreDb.collection('users').doc(startAfterId).get();
      if (startAfterDoc.exists) {
        query = query.startAfter(startAfterDoc);
      }
    }

    const usersSnap = await query.limit(limit).get();

    const usersPromises = usersSnap.docs.map(async (doc) => {
      const data = doc.data();
      let taskCount = data.taskCount;

      // Self-healing: if taskCount is not set/not a number, count tasks and cache it
      if (typeof taskCount !== 'number') {
        try {
          const countSnap = await firestoreDb.collection('tasks').where('uid', '==', doc.id).count().get();
          taskCount = countSnap.data().count || 0;
          // Update the user document asynchronously so it's cached next time
          firestoreDb.collection('users').doc(doc.id).update({ taskCount }).catch(() => {});
        } catch (countErr) {
          console.error(`Failed to calculate taskCount for ${doc.id}:`, countErr);
          taskCount = 0;
        }
      }

      return {
        uid: doc.id,
        email: data.email || 'N/A',
        displayName: data.displayName || 'N/A',
        lastSeenAt: data.lastSeenAt || null,
        updatedTimestamp: data.updatedTimestamp || null,
        blocked: data.blocked || false,
        gender: data.gender || null,
        age: data.age || null,
        appVersionCode: data.appVersionCode || 0,
        appVersionName: data.appVersionName || 'N/A',
        taskCount
      };
    });

    const users = await Promise.all(usersPromises);

    res.json({ 
      users, 
      nextOffsetId: users.length === limit ? users[users.length - 1].uid : null
    });
  } catch (err) {
    console.error('Error fetching users:', err);
    res.status(500).json({ error: 'Failed to fetch users' });
  }
});

// Get user profile details & recent tasks
app.get('/api/users/:uid', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const userDoc = await firestoreDb.collection('users').doc(uid).get();
    if (!userDoc.exists) {
      return res.status(404).json({ error: 'User not found' });
    }
    const userData = userDoc.data();

    let authUser = null;
    try {
      authUser = await admin.auth().getUser(uid);
    } catch (e) { /* user not in firebase auth list */ }

    // Fetch at most 100 recent tasks to prevent large payload server freezes
    const tasksSnap = await firestoreDb.collection('tasks')
      .where('uid', '==', uid)
      .orderBy('createdTimestamp', 'desc')
      .limit(100)
      .get();

    const tasks = tasksSnap.docs.map(doc => ({ docId: doc.id, ...doc.data() }));

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
        appVersionCode: userData.appVersionCode || 0,
        appVersionName: userData.appVersionName || 'N/A',
        entitlement_tier: userData.entitlement_tier || 'FREE_TIER',
        entitlement_expires_at: userData.entitlement_expires_at || 0,
        entitlement_student_expires_at: userData.entitlement_student_expires_at || 0,
        entitlement_youngster_expires_at: userData.entitlement_youngster_expires_at || 0,
        entitlement_activated_at: userData.entitlement_activated_at || 0,
        disabled: authUser?.disabled || false,
        creationTime: authUser?.metadata?.creationTime || null,
        lastSignInTime: authUser?.metadata?.lastSignInTime || null
      },
      tasks,
      tagOverrides
    });
  } catch (err) {
    console.error('Error fetching user detail:', err);
    res.status(500).json({ error: 'Failed to fetch user details' });
  }
});

// Update entitlements
const VALID_TIERS = ['FREE_TIER', 'UNPREMIUM', 'PROMOTIONAL', 'PREMIUM', 'PREMIUM_STUDENT', 'PREMIUM_YOUNGSTER'];
app.post('/api/users/:uid/entitlement', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const { tier, expiresAtMs, studentValidityExpiresAtMs, youngsterValidityExpiresAtMs } = req.body;

    if (!tier || !VALID_TIERS.includes(tier)) {
      return res.status(400).json({ error: 'Invalid entitlement tier' });
    }

    const now = Date.now();
    const userRef = firestoreDb.collection('users').doc(uid);
    const existing = await userRef.get();

    const payload = {
      entitlement_tier: tier,
      entitlement_expires_at: Number.isFinite(+expiresAtMs) ? +expiresAtMs : 0,
      entitlement_student_expires_at: Number.isFinite(+studentValidityExpiresAtMs) ? +studentValidityExpiresAtMs : 0,
      entitlement_youngster_expires_at: Number.isFinite(+youngsterValidityExpiresAtMs) ? +youngsterValidityExpiresAtMs : 0,
      entitlement_updated_at: now,
      entitlement_updated_by: req.session.user.email,
    };
    if (!existing.exists || !existing.data().entitlement_activated_at) {
      payload.entitlement_activated_at = now;
    }

    await userRef.set(payload, { merge: true });
    res.json({ success: true, entitlement: payload });
  } catch (err) {
    console.error('Error saving entitlement:', err);
    res.status(500).json({ error: 'Failed to update entitlement' });
  }
});

// Block/unblock user accounts
app.post('/api/users/:uid/block', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const { blocked } = req.body;

    await firestoreDb.collection('users').doc(uid).update({ blocked: !!blocked });
    await admin.auth().updateUser(uid, { disabled: !!blocked });

    res.json({ success: true, blocked: !!blocked });
  } catch (err) {
    console.error('Error blocking user:', err);
    res.status(500).json({ error: 'Failed to update user block status' });
  }
});

// ─── TASK MANAGEMENT ROUTES (VERSION CODE COMPATIBLE) ───

// Helper: encode/decode Firestore doc IDs (same as Android app)
function encodeDocId(raw) { return raw.replace(/\//g, '%2F'); }

// Create task with version guard
app.post('/api/users/:uid/tasks', requireAuth, async (req, res) => {
  try {
    const { uid } = req.params;
    const taskId = req.body.id || generateId();
    const now = Date.now();
    const today = new Date().toISOString().split('T')[0];

    // Fetch user device version code
    const userDoc = await firestoreDb.collection('users').doc(uid).get();
    const appVersionCode = userDoc.exists ? (userDoc.data().appVersionCode || 0) : 0;

    const isHabit = !!req.body.isHabit;
    const isEvent = !!req.body.isEvent;

    // Version Guard: Prevent creating habit/event configurations if client is running legacy v1–v7 (< 8)
    if (appVersionCode < 8 && (isHabit || isEvent || req.body.recurrenceInterval || req.body.recurrenceDays)) {
      return res.status(400).json({ 
        error: `Legacy client warning: User is running app version code ${appVersionCode}. This version does not support habits, events, or custom recurrence settings.` 
      });
    }

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
      isHabit: isHabit,
      habitSuperStreakCount: req.body.habitSuperStreakCount || 0,
      isEvent: isEvent,
      eventColor: req.body.eventColor || null,
      eventIcon: req.body.eventIcon || null
    };

    const docId = encodeDocId(`${uid}::${taskId}`);
    await firestoreDb.collection('tasks').doc(docId).set(task);

    // Increment user task counter locally as fallback (Cloud Function handles this on sync anyway)
    await firestoreDb.collection('users').doc(uid).update({
      taskCount: admin.firestore.FieldValue.increment(1)
    }).catch(() => {});

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
    delete updates.docId;

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
    const { docId } = req.params;
    
    // Resolve user UID to decrement task counter
    const taskDoc = await firestoreDb.collection('tasks').doc(docId).get();
    if (taskDoc.exists) {
      const uid = taskDoc.data().uid;
      if (uid) {
        await firestoreDb.collection('users').doc(uid).update({
          taskCount: admin.firestore.FieldValue.increment(-1)
        }).catch(() => {});
      }
    }

    await firestoreDb.collection('tasks').doc(docId).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting task:', err);
    res.status(500).json({ error: 'Failed to delete task' });
  }
});

// Mass task deletion
app.post('/api/tasks/mass_delete', requireAuth, async (req, res) => {
  try {
    const { status, beforeDate } = req.body;
    let query = firestoreDb.collection('tasks');
    
    if (status === 'completed') {
      query = query.where('isCompleted', '==', true);
    } else if (status === 'active') {
      query = query.where('isCompleted', '==', false);
    }
    
    const snapshot = await query.get();
    let deletedCount = 0;
    const batchArray = [];
    let batch = firestoreDb.batch();
    let count = 0;
    
    snapshot.docs.forEach((doc) => {
      const data = doc.data();
      if (beforeDate) {
        if (data.createdDate && data.createdDate < beforeDate) {
           batch.delete(doc.ref);
           count++;
           deletedCount++;
        }
      } else {
        batch.delete(doc.ref);
        count++;
        deletedCount++;
      }
      
      if (count === 500) {
        batchArray.push(batch.commit());
        batch = firestoreDb.batch();
        count = 0;
      }
    });
    
    if (count > 0) batchArray.push(batch.commit());
    await Promise.all(batchArray);
    
    // Invalidate stats cache
    cache.del('dashboard_stats');
    
    res.json({ success: true, deletedCount });
  } catch (err) {
    console.error('Error mass deleting tasks:', err);
    res.status(500).json({ error: 'Failed to mass delete tasks' });
  }
});

// ─── USER GROUP SEGMENTATION ROUTES ───

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

app.post('/api/groups', requireAuth, async (req, res) => {
  try {
    const id = generateId();
    const group = {
      name: req.body.name || 'Untitled Group',
      description: req.body.description || null,
      filterType: req.body.filterType || 'manual', // manual, gender, age, version
      filterGender: req.body.filterGender || null,
      filterAgeMin: req.body.filterAgeMin ? parseInt(req.body.filterAgeMin) : null,
      filterAgeMax: req.body.filterAgeMax ? parseInt(req.body.filterAgeMax) : null,
      filterVersionMin: req.body.filterVersionMin ? parseInt(req.body.filterVersionMin) : null,
      manualUids: req.body.manualUids || [],
      createdAt: Date.now()
    };

    await firestoreDb.collection('user_groups').doc(id).set(group);
    res.json({ success: true, id, group });
  } catch (err) {
    console.error('Error creating group:', err);
    res.status(500).json({ error: 'Failed to create group' });
  }
});

app.delete('/api/groups/:id', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('user_groups').doc(req.params.id).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting group:', err);
    res.status(500).json({ error: 'Failed to delete group' });
  }
});

// Optimized Group Membership Resolution (No complete memory scan unless manual)
async function resolveGroupMembers(group) {
  if (group.filterType === 'manual') {
    return group.manualUids || [];
  }

  let query = firestoreDb.collection('users');

  // Query filtering using Firestore index matches
  if (group.filterGender) {
    query = query.where('gender', '==', group.filterGender);
  }

  const snapshot = await query.get();
  const uids = [];

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (data.blocked) continue;

    // Secondary filters done in-memory to prevent requiring composite index explosions
    if (group.filterAgeMin != null && (data.age == null || data.age < group.filterAgeMin)) continue;
    if (group.filterAgeMax != null && (data.age == null || data.age > group.filterAgeMax)) continue;
    if (group.filterVersionMin != null && (data.appVersionCode == null || data.appVersionCode < group.filterVersionMin)) continue;

    uids.push(doc.id);
  }

  return uids;
}

app.get('/api/groups/:id/members', requireAuth, async (req, res) => {
  try {
    const groupDoc = await firestoreDb.collection('user_groups').doc(req.params.id).get();
    if (!groupDoc.exists) return res.status(404).json({ error: 'Group not found' });

    const uids = await resolveGroupMembers(groupDoc.data());
    res.json({ count: uids.length, uids });
  } catch (err) {
    console.error('Error resolving group members:', err);
    res.status(500).json({ error: 'Failed to resolve members' });
  }
});

// ─── BROADCAST (IN-APP ANNOUNCEMENT) ROUTES ───

app.get('/api/broadcasts', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('broadcasts').orderBy('createdAt', 'desc').get();
    const broadcasts = snap.docs.map(doc => {
      const data = doc.data();
      if (typeof data.tags === 'string') {
        try { data.tags = JSON.parse(data.tags); } catch(e) { data.tags = []; }
      }
      return { id: doc.id, ...data };
    });
    res.json({ broadcasts });
  } catch (err) {
    console.error('Error fetching broadcasts:', err);
    res.status(500).json({ error: 'Failed to fetch broadcasts' });
  }
});

// Create Broadcast + FCM notification logic (Safe targeting integration)
app.post('/api/broadcasts', requireAuth, async (req, res) => {
  try {
    const id = generateId();
    const now = Date.now();

    let tags = [];
    if (req.body.tags) {
      try {
        tags = typeof req.body.tags === 'string' ? JSON.parse(req.body.tags) : req.body.tags;
      } catch (e) { tags = []; }
    }

    const broadcast = {
      title: req.body.title || 'Untitled Announcement',
      description: req.body.description || null,
      imageUrl: req.body.imageUrl || null,
      actionUrl: req.body.actionUrl || null,
      deepLink: req.body.deepLink || null,
      actionLabel: req.body.actionLabel || 'Open Now',
      type: req.body.type || 'announcement',
      tags: tags,
      directRedirect: req.body.directRedirect === true,
      priority: parseInt(req.body.priority) || 0,
      createdAt: now,
      expiresAt: req.body.expiresAt ? parseInt(req.body.expiresAt) : null,
      active: req.body.active !== false,
      featureKey: req.body.featureKey || null,
      dismissible: req.body.dismissible !== false,
      targetType: req.body.targetType || 'all', // all, single, group
      targetGroupId: req.body.targetGroupId || null,
      targetUids: req.body.targetUids || null
    };

    await firestoreDb.collection('broadcasts').doc(id).set(broadcast);

    // Send FCM push alerts to the target subset only if autoNotify is true
    let notifySent = 0;
    if (broadcast.active && req.body.autoNotify === true) {
      const campaignId = `broadcast_${id}_${now.toString(36)}`;
      const dataPayload = {
        title: broadcast.title,
        body: broadcast.description || broadcast.title,
        channelType: 'broadcast',
        deepLink: broadcast.deepLink || 'preamble://home',
        campaign_id: campaignId,
        campaign_variant: 'default'
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
          // Broadcast to all active tokens
          const usersSnap = await firestoreDb.collection('users').get();
          const tokens = [];
          usersSnap.docs.forEach(doc => {
            const data = doc.data();
            if (data.fcmToken && !data.blocked) tokens.push(data.fcmToken);
          });
          notifySent = await sendToTokens(tokens, dataPayload);
        }

        // Log notification record
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
        console.error('FCM broadcast notification error:', notifyErr);
      }
    }

    res.json({ success: true, id, broadcast, notifySent });
  } catch (err) {
    console.error('Error creating broadcast:', err);
    res.status(500).json({ error: 'Failed to create broadcast' });
  }
});

app.delete('/api/broadcasts/:id', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('broadcasts').doc(req.params.id).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting broadcast:', err);
    res.status(500).json({ error: 'Failed to delete broadcast' });
  }
});

// Toggle active status
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

// ─── PUSH NOTIFICATIONS & FCM CAMPAIGN ROUTES ───

// Helper: resolve FCM tokens from UID list
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

// Multicast batch dispatcher (sends in slices of 500)
async function sendToTokens(tokens, dataPayload) {
  const uniqueTokens = [...new Set(tokens)];
  if (uniqueTokens.length === 0) return 0;
  let sent = 0;
  for (let i = 0; i < uniqueTokens.length; i += 500) {
    const batch = uniqueTokens.slice(i, i + 500);
    const response = await admin.messaging().sendEachForMulticast({
      tokens: batch,
      data: dataPayload,
      android: { priority: 'high' }
    });
    sent += response.successCount;
  }
  return sent;
}

// Send FCM alerts (WITH STRICTOR PARAMETER VALIDATIONS)
app.post('/api/notifications/send', requireAuth, async (req, res) => {
  try {
    const { title, body, deepLink, url, channelType, targetType, targetUid, targetGroupId } = req.body;

    if (!title || !body) {
      return res.status(400).json({ error: 'Title and body parameters are required.' });
    }

    // Explicit Validation Guard: Return error on missing properties to prevent fallbacks from mass-spamming
    if (targetType === 'single') {
      if (!targetUid) {
        return res.status(400).json({ error: 'Validation Error: targetUid is required when targetType is set to single.' });
      }
    } else if (targetType === 'group') {
      if (!targetGroupId) {
        return res.status(400).json({ error: 'Validation Error: targetGroupId is required when targetType is set to group.' });
      }
    } else if (targetType !== 'all') {
      return res.status(400).json({ error: 'Validation Error: targetType must be set to either all, group, or single.' });
    }

    const campaignId = req.body.campaign_id || `${title.toLowerCase().replace(/[^a-z0-9]+/g, '_').slice(0, 40)}_${Date.now().toString(36)}`;
    const dataPayload = {
      title,
      body,
      channelType: channelType || 'broadcast',
      campaign_id: campaignId,
      campaign_variant: req.body.campaign_variant || 'default'
    };
    if (deepLink) dataPayload.deepLink = deepLink;
    if (url) dataPayload.url = url;

    let sent = 0;

    if (targetType === 'single') {
      const userDoc = await firestoreDb.collection('users').doc(targetUid).get();
      const token = userDoc.exists ? userDoc.data().fcmToken : null;
      if (!token) {
        return res.status(400).json({ error: 'User does not possess an active FCM push token.' });
      }
      await admin.messaging().send({
        token,
        data: dataPayload,
        android: { priority: 'high' }
      });
      sent = 1;
    } else if (targetType === 'group') {
      const groupDoc = await firestoreDb.collection('user_groups').doc(targetGroupId).get();
      if (!groupDoc.exists) {
        return res.status(404).json({ error: 'Target user group not found.' });
      }
      const uids = await resolveGroupMembers(groupDoc.data());
      sent = await sendNotificationToUids(uids, dataPayload);
    } else {
      // Send to all users with tokens
      const usersSnap = await firestoreDb.collection('users').get();
      const tokens = [];
      usersSnap.docs.forEach(doc => {
        const data = doc.data();
        if (data.fcmToken && !data.blocked) tokens.push(data.fcmToken);
      });
      sent = await sendToTokens(tokens, dataPayload);
    }

    // Save notification log
    await firestoreDb.collection('notification_log').add({
      title,
      body,
      deepLink: deepLink || null,
      url: url || null,
      channelType: channelType || 'broadcast',
      targetType,
      targetUid: targetUid || null,
      targetGroupId: targetGroupId || null,
      sentAt: Date.now(),
      sentCount: sent,
      sentBy: req.session.user.email
    });

    res.json({ success: true, sent });
  } catch (err) {
    console.error('Error triggering notification campaign:', err);
    res.status(500).json({ error: 'Failed to send notification campaign: ' + err.message });
  }
});

app.get('/api/notifications/history', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('notification_log')
      .orderBy('sentAt', 'desc')
      .limit(50)
      .get();
    const history = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    res.json({ history });
  } catch (err) {
    console.error('Error fetching notification logs:', err);
    res.status(500).json({ error: 'Failed to fetch notification history' });
  }
});

// ─── PERSONAL MODE MESSAGE OVERRIDES ───

app.get('/api/pm-messages', requireAuth, async (req, res) => {
  try {
    const snap = await firestoreDb.collection('pm_messages').orderBy('createdAt', 'desc').get();
    const messages = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    res.json({ messages });
  } catch (err) {
    console.error('Error fetching PM messages:', err);
    res.status(500).json({ error: 'Failed to fetch messages' });
  }
});

app.post('/api/pm-messages', requireAuth, async (req, res) => {
  try {
    const id = generateId();
    const msg = {
      type: req.body.type || 'greeting',
      condition: req.body.condition || 'default',
      headline: req.body.headline || '',
      subtitle: req.body.subtitle || null,
      active: req.body.active !== false,
      targetType: req.body.targetType || 'all', // all, user
      targetUids: req.body.targetUids || null,
      priority: parseInt(req.body.priority) || 0,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    await firestoreDb.collection('pm_messages').doc(id).set(msg);
    res.json({ success: true, id, message: msg });
  } catch (err) {
    console.error('Error creating PM message:', err);
    res.status(500).json({ error: 'Failed to create message' });
  }
});

app.put('/api/pm-messages/:id', requireAuth, async (req, res) => {
  try {
    const updates = { ...req.body, updatedAt: Date.now() };
    delete updates.id;
    if (updates.priority) updates.priority = parseInt(updates.priority);

    await firestoreDb.collection('pm_messages').doc(req.params.id).update(updates);
    res.json({ success: true });
  } catch (err) {
    console.error('Error updating PM message:', err);
    res.status(500).json({ error: 'Failed to update message' });
  }
});

app.delete('/api/pm-messages/:id', requireAuth, async (req, res) => {
  try {
    await firestoreDb.collection('pm_messages').doc(req.params.id).delete();
    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting PM message:', err);
    res.status(500).json({ error: 'Failed to delete message' });
  }
});

// ─── PROBLEM REPORTS & SUPPORT TICKETS ROUTES ───

async function attachSignedMediaUrls(report) {
  const bucket = admin.storage().bucket();
  const attachments = Array.isArray(report.attachments) ? report.attachments : [];

  const signedAttachments = await Promise.all(attachments.map(async (attachment) => {
    if (!attachment || !attachment.storagePath) return attachment;
    try {
      const [url] = await bucket.file(attachment.storagePath).getSignedUrl({
        action: 'read',
        expires: Date.now() + 60 * 60 * 1000 // 1 hour token
      });
      return { ...attachment, signedUrl: url };
    } catch (err) {
      console.warn('Could not generate signed GCS URL:', attachment.storagePath, err.message);
      return { ...attachment, signedUrl: null };
    }
  }));

  return { ...report, attachments: signedAttachments };
}

// Get paginated problem reports
app.get('/api/problem-reports', requireAuth, async (req, res) => {
  try {
    const filterStatus = req.query.status || 'all';
    const limit = Math.min(parseInt(req.query.limit) || 20, 100);
    const startAfterId = req.query.startAfter;

    let query = firestoreDb.collection('problemReports').orderBy('updatedAt', 'desc');

    if (filterStatus !== 'all') {
      query = query.where('status', '==', filterStatus);
    }

    if (startAfterId) {
      const doc = await firestoreDb.collection('problemReports').doc(startAfterId).get();
      if (doc.exists) query = query.startAfter(doc);
    }

    const snap = await query.limit(limit).get();
    let reports = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

    reports = await Promise.all(reports.map(attachSignedMediaUrls));

    res.json({ 
      reports,
      nextOffsetId: reports.length === limit ? reports[reports.length - 1].id : null
    });
  } catch (err) {
    console.error('Error fetching problem reports:', err);
    res.status(500).json({ error: 'Failed to fetch problem reports' });
  }
});

// Update status & manage transactional block gates
app.put('/api/problem-reports/:id/status', requireAuth, async (req, res) => {
  try {
    const { status, adminNote } = req.body;
    if (!['open', 'in_progress', 'resolved'].includes(status)) {
      return res.status(400).json({ error: 'Invalid report status' });
    }

    const now = Date.now();
    const updates = {
      status,
      adminNote: adminNote || null,
      updatedAt: now,
      statusUpdatedAt: now,
      statusUpdatedBy: req.session.user.email,
      resolvedAt: status === 'resolved' ? now : null
    };

    const reportRef = firestoreDb.collection('problemReports').doc(req.params.id);
    await firestoreDb.runTransaction(async (tx) => {
      const reportDoc = await tx.get(reportRef);
      if (!reportDoc.exists) {
        throw Object.assign(new Error('Problem report not found'), { statusCode: 404 });
      }

      const report = reportDoc.data() || {};
      const uid = report.uid;
      const gateRef = uid ? firestoreDb.collection('problemReportGates').doc(uid) : null;
      const gateDoc = gateRef ? await tx.get(gateRef) : null;

      tx.update(reportRef, updates);

      if (gateRef) {
        if (status === 'resolved') {
          if (!gateDoc?.exists || gateDoc.data()?.activeReportId === req.params.id) {
            tx.delete(gateRef);
          }
        } else {
          tx.set(gateRef, {
            uid,
            activeReportId: req.params.id,
            status,
            updatedAt: now
          }, { merge: true });
        }
      }
    });

    cache.del('dashboard_stats');
    res.json({ success: true, updates });
  } catch (err) {
    console.error('Error saving status update on report:', err);
    res.status(err.statusCode || 500).json({ error: err.message || 'Failed to update problem report' });
  }
});

app.delete('/api/problem-reports/:id', requireAuth, async (req, res) => {
  try {
    const reportRef = firestoreDb.collection('problemReports').doc(req.params.id);
    const reportDoc = await reportRef.get();
    if (!reportDoc.exists) {
      return res.status(404).json({ error: 'Problem report not found' });
    }

    const report = reportDoc.data() || {};
    if (report.status !== 'resolved') {
      return res.status(400).json({ error: 'Only resolved tickets are eligible for deletion.' });
    }

    const attachments = Array.isArray(report.attachments) ? report.attachments : [];

    await firestoreDb.runTransaction(async (tx) => {
      const latestReport = await tx.get(reportRef);
      if (!latestReport.exists) {
        throw Object.assign(new Error('Problem report not found'), { statusCode: 404 });
      }
      if (latestReport.data()?.status !== 'resolved') {
        throw Object.assign(new Error('Only resolved tickets are eligible for deletion.'), { statusCode: 400 });
      }

      const uid = latestReport.data()?.uid;
      const gateRef = uid ? firestoreDb.collection('problemReportGates').doc(uid) : null;
      const gateDoc = gateRef ? await tx.get(gateRef) : null;
      if (gateRef && gateDoc?.exists && gateDoc.data()?.activeReportId === req.params.id) {
        tx.delete(gateRef);
      }
      tx.delete(reportRef);
    });

    // Clean up media assets from Cloud Storage
    const bucket = admin.storage().bucket();
    const deletedAttachments = [];
    for (const attachment of attachments) {
      if (!attachment?.storagePath) continue;
      try {
        await bucket.file(attachment.storagePath).delete({ ignoreNotFound: true });
        deletedAttachments.push(attachment.storagePath);
      } catch (err) {
        console.warn('GCS Cleanup warning:', attachment.storagePath, err.message);
      }
    }

    cache.del('dashboard_stats');
    res.json({ success: true, deletedAttachments: deletedAttachments.length });
  } catch (err) {
    console.error('Error deleting report:', err);
    res.status(err.statusCode || 500).json({ error: err.message || 'Failed to delete problem report' });
  }
});

// ─── DASHBOARD STATS ROUTE (O(1) CACHED META-DOCUMENT ACCESS) ───

app.get('/api/stats', requireAuth, async (req, res) => {
  try {
    const cachedStats = cache.get('dashboard_stats');
    if (cachedStats) return res.json(cachedStats);

    // Try reading pre-aggregated dashboard doc
    const statsDoc = await firestoreDb.collection('stats').doc('dashboard').get();
    if (statsDoc.exists) {
      const statsData = statsDoc.data();
      cache.set('dashboard_stats', statsData);
      return res.json(statsData);
    }

    // Fallback: Perform heavy count scans if metadata document doesn't exist yet (first-run setup)
    console.log('Stats meta-document missing. Running aggregate fallback scan...');
    const usersSnap = await firestoreDb.collection('users').get();
    const tasksSnap = await firestoreDb.collection('tasks').get();
    const reportsSnap = await firestoreDb.collection('problemReports').get();

    let completedTasks = 0;
    let activeTasks = 0;
    let blockedUsers = 0;
    let openProblemReports = 0;

    tasksSnap.docs.forEach(doc => {
      if (doc.data().isCompleted) completedTasks++;
      else activeTasks++;
    });

    usersSnap.docs.forEach(doc => {
      if (doc.data().blocked) blockedUsers++;
    });

    reportsSnap.docs.forEach(doc => {
      const status = doc.data().status || 'open';
      if (status !== 'resolved') openProblemReports++;
    });

    const stats = {
      totalUsers: usersSnap.size,
      totalTasks: tasksSnap.size,
      activeTasks,
      completedTasks,
      blockedUsers,
      openProblemReports,
      updatedAt: Date.now()
    };

    // Save calculation back to Firestore as O(1) cache source of truth
    await firestoreDb.collection('stats').doc('dashboard').set(stats);
    cache.set('dashboard_stats', stats);

    res.json(stats);
  } catch (err) {
    console.error('Error retrieving aggregates:', err);
    res.status(500).json({ error: 'Failed to retrieve stats' });
  }
});

app.get('/api/stats/posthog', requireAuth, async (req, res) => {
  const apiKey = process.env.POSTHOG_PERSONAL_API_KEY;
  const projectId = process.env.POSTHOG_PROJECT_ID;

  if (!apiKey || !projectId) {
    return res.status(503).json({ error: 'PostHog analytics integration is not configured in .env' });
  }

  const cachedData = cache.get('posthog_funnel_stats');
  if (cachedData) return res.json(cachedData);

  try {
    const url = `https://us.posthog.com/api/projects/${projectId}/query`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: {
          kind: 'TrendsQuery',
          series: [
            { kind: 'EventsNode', event: '$app_install', math: 'dau' },
            { kind: 'EventsNode', event: 'onboarding_completed', math: 'dau' },
            { kind: 'EventsNode', event: 'google_sync', math: 'dau' }
          ],
          dateRange: { date_from: '-30d' }
        }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`PostHog HTTP Error ${response.status}: ${errText}`);
    }

    const data = await response.json();
    const results = data.results || [];
    const installObj = results.find(r => r.label === '$app_install') || { count: 0 };
    const onboardingObj = results.find(r => r.label === 'onboarding_completed') || { count: 0 };
    const syncObj = results.find(r => r.label === 'google_sync') || { count: 0 };

    const payload = {
      installs: installObj.count || 0,
      onboarded: onboardingObj.count || 0,
      synced: syncObj.count || 0,
      updatedAt: Date.now()
    };

    cache.set('posthog_funnel_stats', payload, 3600); // cache for 1 hour
    res.json(payload);
  } catch (err) {
    console.error('Error fetching PostHog query:', err);
    res.status(500).json({ error: 'Failed to retrieve PostHog analytics' });
  }
});

app.get('/api/stats/posthog/events', requireAuth, async (req, res) => {
  const apiKey = process.env.POSTHOG_PERSONAL_API_KEY;
  const projectId = process.env.POSTHOG_PROJECT_ID;

  if (!apiKey || !projectId) {
    return res.status(503).json({ error: 'PostHog analytics integration is not configured' });
  }

  try {
    const url = `https://us.posthog.com/api/projects/${projectId}/events/?limit=40`;
    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      }
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`PostHog Events Error ${response.status}: ${errText}`);
    }

    const data = await response.json();
    res.json({ events: data.results || [] });
  } catch (err) {
    console.error('Error fetching PostHog events:', err);
    res.status(500).json({ error: 'Failed to fetch PostHog events log' });
  }
});

app.get('/api/stats/posthog/trends', requireAuth, async (req, res) => {
  const apiKey = process.env.POSTHOG_PERSONAL_API_KEY;
  const projectId = process.env.POSTHOG_PROJECT_ID;

  if (!apiKey || !projectId) {
    return res.status(503).json({ error: 'PostHog analytics integration is not configured' });
  }

  const cachedData = cache.get('posthog_trends_stats');
  if (cachedData) return res.json(cachedData);

  try {
    const url = `https://us.posthog.com/api/projects/${projectId}/query`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: {
          kind: 'TrendsQuery',
          series: [
            { kind: 'EventsNode', event: 'task_created', math: 'total' },
            { kind: 'EventsNode', event: 'task_completed', math: 'total' },
            { kind: 'EventsNode', event: 'ai_interaction', math: 'total' },
            { kind: 'EventsNode', event: 'app_crashed', math: 'total' }
          ],
          dateRange: { date_from: '-14d' }
        }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`PostHog Trends HTTP Error ${response.status}: ${errText}`);
    }

    const data = await response.json();
    const results = data.results || [];
    
    const createdObj = results.find(r => r.label === 'task_created') || { data: [], labels: [] };
    const completedObj = results.find(r => r.label === 'task_completed') || { data: [], labels: [] };
    const aiObj = results.find(r => r.label === 'ai_interaction') || { data: [], labels: [] };
    const crashedObj = results.find(r => r.label === 'app_crashed') || { data: [], labels: [] };

    const payload = {
      labels: createdObj.labels || completedObj.labels || aiObj.labels || [],
      created: createdObj.data || [],
      completed: completedObj.data || [],
      ai: aiObj.data || [],
      crashes: crashedObj.data || []
    };

    cache.set('posthog_trends_stats', payload, 1800); // cache for 30 minutes
    res.json(payload);
  } catch (err) {
    console.error('Error fetching PostHog trends:', err);
    res.status(500).json({ error: 'Failed to retrieve PostHog trends' });
  }
});

// ─── AI CONFIGURATION MANAGEMENT ROUTES ───

// Read AI config (parseModel, chatModel, killSwitch)
app.get('/api/ai/config', requireAuth, async (req, res) => {
  try {
    const doc = await firestoreDb.collection('config').doc('ai').get();
    const config = doc.exists ? doc.data() : {
      parseModel: 'gemini-2.5-flash-lite',
      chatModel: 'gemini-2.5-flash',
      killSwitch: false,
    };
    res.json({ config });
  } catch (err) {
    console.error('Error reading AI config:', err);
    res.status(500).json({ error: 'Failed to read AI config' });
  }
});

// Update AI config (service account bypasses Firestore rules)
app.put('/api/ai/config', requireAuth, async (req, res) => {
  try {
    const { parseModel, chatModel, killSwitch } = req.body;
    const update = {
      updatedAt: Date.now(),
      updatedBy: req.session.user.email,
    };
    if (parseModel !== undefined) update.parseModel = parseModel;
    if (chatModel !== undefined) update.chatModel = chatModel;
    if (killSwitch !== undefined) update.killSwitch = !!killSwitch;

    await firestoreDb.collection('config').doc('ai').set(update, { merge: true });

    console.log(`[AI Config] Updated by ${req.session.user.email}: parseModel=${parseModel}, chatModel=${chatModel}, killSwitch=${killSwitch}`);
    res.json({ success: true, config: update });
  } catch (err) {
    console.error('Error updating AI config:', err);
    res.status(500).json({ error: 'Failed to update AI config' });
  }
});

// ─── AI ASSISTANT / GENERATION ROUTES ───

app.post('/api/ai/chat', requireAuth, async (req, res) => {
  try {
    const { prompt } = req.body;
    if (!ai) return res.status(500).json({ error: 'Gemini AI API is not configured on this server.' });

    const statsDoc = await firestoreDb.collection('stats').doc('dashboard').get();
    const stats = statsDoc.exists ? statsDoc.data() : { note: 'aggregates unavailable' };
    
    const systemPrompt = `You are Preamble Admin AI, a helpful, advanced admin assistant.
Current Dashboard Stats: ${JSON.stringify(stats)}
You help the admin manage the app, answer questions, and automate tasks.
If the admin asks to perform an action, respond with a JSON block formatted EXACTLY like this:
\`\`\`json
{
  "action": "action_name",
  "payload": { ... }
}
\`\`\`
Available actions:
- create_broadcast: { "title": "...", "description": "...", "targetType": "all" }
- mass_delete_tasks: { "status": "completed", "beforeDate": "YYYY-MM-DD" }

Or simply reply conversationally. Keep responses concise, clean, and professional. Use markdown for styling.`;

    const response = await ai.models.generateContent({
        model: 'gemini-2.5-pro',
        contents: prompt,
        config: {
            systemInstruction: systemPrompt
        }
    });
    
    res.json({ reply: response.text });
  } catch (err) {
    console.error('Gemini chat execution error:', err);
    res.status(500).json({ error: 'AI processing failed' });
  }
});

// Copywriting generator (Mistral)
app.post('/api/ai/generate', requireAuth, async (req, res) => {
  try {
    const { type, context } = req.body;
    const key = process.env.MISTRAL_API_KEY || 'kGPnSqNxoGLBMDY4acTyhVbprn0MZRJ0';

    const systemPrompt = `You are a creative copywriter for "Preamble", a modern task management app. Write short, engaging, and friendly content. Be concise — max 2 sentences. Use emoji sparingly. Match the tone: motivational for task-related content, warm for greetings, informative for announcements.`;

    let userPrompt = '';
    switch (type) {
      case 'notification':
        userPrompt = `Generate a push notification for a task management app. Context: ${context || 'general engagement reminder'}. Return JSON: {"title": "...", "body": "..."}`;
        break;
      case 'broadcast':
        userPrompt = `Generate an in-app announcement card for a task management app. Context: ${context || 'new feature announcement'}. Return JSON: {"title": "...", "description": "...", "actionLabel": "..."}`;
        break;
      case 'pm_message':
        userPrompt = `Generate a personal mode message for a task management app. Context: ${context || 'morning greeting'}. Return JSON: {"headline": "...", "subtitle": "..."}`;
        break;
      default:
        userPrompt = `Generate short motivational text. Context: ${context || 'productivity'}. Return JSON: {"text": "..."}`;
    }

    const response = await fetch('https://api.mistral.ai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${key}`
      },
      body: JSON.stringify({
        model: 'mistral-small-latest',
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt }
        ],
        temperature: 0.8,
        max_tokens: 200,
        response_format: { type: 'json_object' }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`Mistral API error: ${response.status} — ${errText}`);
    }

    const data = await response.json();
    const content = data.choices?.[0]?.message?.content;

    let parsed;
    try {
      parsed = JSON.parse(content);
    } catch (e) {
      parsed = { text: content };
    }

    res.json({ success: true, generated: parsed });
  } catch (err) {
    console.error('Mistral text generation failed:', err);
    res.status(500).json({ error: 'Mistral copywriting execution failed: ' + err.message });
  }
});

// Serve index.html SPA entry for other requests
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

// ID Generator helper
function generateId() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let id = '';
  for (let i = 0; i < 20; i++) {
    id += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return id;
}

app.listen(PORT, () => {
  console.log(`\n  ✦ New Preamble Admin Panel backend listening at http://localhost:${PORT}\n`);
});
