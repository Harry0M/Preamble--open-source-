# 🔍 Permanent Notification Debugging Guide

## Problem: Notification Gets Dismissed on Swipe

Ab notification still swipe se close ho rahi hai despite foreground service implementation. Main reason check karne ke instructions yahan hain.

---

## Step 1: Check Logcat (Android Studio)

### Connect Device & Open Logcat

1. **Device connect karo** (USB debugging enabled)
2. Android Studio kholo → **Logcat** tab (bottom of screen)
3. Filter set karo: **"TaskNotificationService"**

### Expected Logs (Service Running)

Jab app khule, ye logs dikhne chahiye:

```
D/TaskNotificationService: Starting TaskNotificationService
D/TaskNotificationService: Service start intent sent
D/TaskNotificationService: TaskNotificationService onCreate called
D/TaskNotificationService: Creating notification channel
D/TaskNotificationService: Notification channel created: preamble_tasks_persistent
D/TaskNotificationService: Starting foreground notification
D/TaskNotificationService: Using FOREGROUND_SERVICE_TYPE_SPECIAL_USE (API 34+)
D/TaskNotificationService: Foreground notification started successfully
D/TaskNotificationService: Service started and observer attached
```

---

## ⚠️ Issues to Check & Fix

### Issue 1: "Service start intent sent" के बाद कोई onCreate log नहीं

**Meaning**: Service start nahi ho rahi hai

**Possible Causes**:
- ❌ Manifest declaration missing
- ❌ Foreground service permission denied
- ❌ Background execution restrictions

**Fix**:
1. **Phone Settings** → **Apps** → **Preamble** → **Battery**
2. Set to **"Unrestricted"** or **"Optimized"**
3. `App power consumption`: **Allow**
4. Restart app

### Issue 2: "Failed to start foreground: ..." error log

**Meaning**: `startForeground()` call failing

**Possible Causes**:
- Notification permission not granted
- Invalid notification channel
- API < 31 and no ForegroundService permission

**Fix**:
1. Settings → Notifications → **Enable for Preamble**
2. App में Settings tab → Notifications → Permission grant करो
3. App force-stop + restart

### Issue 3: Service logs दिख रहे हैं but notification swipeable है

**Meaning**: Service चल रही है पर notification configuration गलत है

**Check Logs**:
- "Foreground notification started successfully" दिखे?
- "Notification updated successfully" दिखे?

**If Yes**: यह Android 16 specific issue है, Android limitations हैं

**Workaround**:
```kotlin
// Android 16+ में special handling
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    // Additional flags needed
    builder.setForegroundServiceBehavior(FOREGROUND_SERVICE_STICKY)
}
```

---

## Step 2: Check Notification Permissions

Open **Settings** and verify:

```
Settings → Notifications → Preamble
├─ [ ✅ ] Allow notifications
├─ [ ✅ ] Allow popping on screen
├─ [ ✅ ] Allow sound
└─ [ ✅ ] Allow vibration (optional)
```

### Check Battery Restrictions:

```
Settings → Battery & device care → Battery usage
└─ Preamble: [Unrestricted]  (NOT "Background limitation")
```

### Check Battery Optimization Whitelist:

```
Settings → Battery
├─ Battery Optimization
   └─ Preamble: [Excluded]  (NOT "Optimized")
```

---

## Step 3: Verify Foreground Service Permission

Check **AndroidManifest.xml** में:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".notification.TaskNotificationService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Persistent task notification" />
</service>
```

✅ **Expected**: Ya sab permissions + declaration मिलना चाहिए

---

## Step 4: Run Debug Session

### Method 1: Via Debug Screen (In-App)

1. App खोलो
2. Settings tab → Notifications  
3. **"🔧 Notification Debug"** tap करो
4. **"Start/Restart Service"** button tap करो
5. Wait 2 seconds
6. Status should show **✅ Running**

### Method 2: Via Logcat Command

Terminal/PowerShell में :

```powershell
# Device से logcat stream करो
adb logcat | findstr TaskNotificationService

# जब task add करो, ये log आना चाहिए:
# D/TaskNotificationService: Updating notification: pendingCount=X
# D/TaskNotificationService: Notification updated successfully
```

---

## Complete Checklist

Before "notification still dismissing" कहो, verify करो:

| Item | Status |
|------|--------|
| ✅ Logcat में service start logs दिख रहे हैं | [ ] |
| ✅ Notification permission granted in phone settings | [ ] |
| ✅ Battery optimization disabled for Preamble | [ ] |
| ✅ "Foreground notification started successfully"log visible | [ ] |
| ✅ Task add करने पर notification update logs visible | [ ] |
| ✅ App force-stopped और restart किया गया है | [ ] |
| ✅ Debug screen pe service running दिख रहा है | [ ] |

---

## Expected Behavior (Android 16)

### ✅ Correct Setup
- App start → notification appears
- Swipe → notification stays (dismissible only by app logic)
- Task add → notification updates instantly
- Background → notification persists

### ❌ Wrong Setup  
- Service logs न दिख रहे हों
- Notification update logs न दिख रहे हों
- Permission warnings दिख रहे हों
- Notification immediately dismiss हो रहा हो

---

## Common Logcat Error Messages

### Error 1: "SecurityException: startForeground"
```
E/AndroidRuntime: SecurityException: startForeground() called without POST_NOTIFICATIONS permission
```
**Fix**: Grant notification permission in phone settings

### Error 2: "Service not allowed to start"
```
E/ActivityManager: java.lang.IllegalStateException: Service not allowed to start in the background
```
**Fix**: Add Preamble to battery optimization whitelist

### Error 3: "Channel blocked"
```
E/NotificationManager: NotificationChannel is blocked
```
**Fix**: Check notification channel settings for your app

---

## Advanced Debugging (For Technical Users)

### Check Service State via ADB

```powershell
# Service चल रहा है या नहीं check करो
adb shell dumpsys activity services | findstr "TaskNotificationService"

# Expected Output:
# * TaskNotificationService
#   app=ProcessRecord(... preamble ...)
```

### Check Foreground Services

```powershell
# सभी running foreground services देखो
adb shell cmd activity services list foreground | findstr preamble

# Expected: TaskNotificationService should be listed
```

### Monitor Notification Changes

```powershell
# Real-time notification updates देखो  
adb logcat | findstr "Notification"
```

---

## Next Steps (यदि issue persist करे)

1. **Logcat output share करो** - Task notification / Service logs के साथ
2. **Phone model & Android version** - बताना (e.g., "Samsung Galaxy S23, Android 16")
3. **Settings screenshots** - Battery & Notification settings की
4. **Error messages** - Logcat से exact error

---

## Possible Root Causes

### Android OS Level Issues (India specific)
- Some OEMs (Xiaomi, Oppo, Vivo) have aggressive background task killing
- May require additional whitelisting in battery optimization

### App-Level Issues
- Service not receiving START_STICKY properly
- Notification permission granted but notification transport blocked
- Background execution policy restricting foreground service

### Device-Level Issues
- Low RAM causing service to be killed
- Aggressive power saving mode
- System notification settings blocking special-use services

---

**Logcat output लेकर ये checklist complete करो, फिर आगे का step बताऊंगा!** 🔍
