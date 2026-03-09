# 🔧 Foreground Service Setup Guide

## ✅ Service Auto-Setup Ho Gaya Hai!

**Koi manual setup nahi chahiye!** Service automatically start ho jaati hai jab app khulti hai.

---

## 📱 Notification Dekhne Ke Steps

### Step 1: App Kholo
- Preamble app launch karo
- Service automatically background me start ho jayegi

### Step 2: Notification Tray Check Karo
- Swipe down from top (notification tray)
- **"Preamble"** notification dikhni chahiye
- Notification me dikhega:
  - Pending task count (e.g., "3 tasks pending")
  - **Quick Add** button
  - **🎙 Voice** button
  - Expand karne par: pending tasks ki list

### Step 3: Swipe Test Karo
- Notification ko left/right swipe try karo
- **Result**: Notification close **NAHI** honi chahiye ✅
- Agar close ho gayi = Service running nahi hai (troubleshoot karo)

---

## 🔍 Agar Notification Nahi Dikh Rahi

### Option 1: Debug Screen Use Karo (Recommended)

1. **Settings tab** kholo (app me sabse right wala tab)
2. **Notifications** section me scroll karo
3. **"🔧 Notification Debug"** option par tap karo
4. Debug screen khulegi showing:
   - ✅/❌ Service Status (Running/Stopped)
   - Notification Permission Status
   - Manual Start/Stop buttons

5. Agar service **❌ Stopped** dikha rahi hai:
   - **"Start/Restart Service"** button tap karo
   - 2 seconds wait karo
   - Status **✅ Running** ho jana chahiye

### Option 2: Notification Permission Check Karo

1. Phone Settings → Apps → Preamble
2. Notifications → **ON** karo
3. App close karke restart karo

### Option 3: App Force Stop Karke Restart Karo

1. Phone Settings → Apps → Preamble → **Force Stop**
2. App dubara kholo
3. Service auto-start hogi

---

## 🎯 Debug Screen Features

### Service Status Card
- **✅ Running**: Service properly chal rahi hai
- **❌ Stopped**: Service band hai (restart button use karo)
- **Last check**: Real-time status (her 2 seconds update hoti hai)

### Buttons
1. **Start/Restart Service**
   - Service manually start karo
   - Agar crash ho gayi ho to reset karne ke liye
   
2. **Stop Service (Test)**
   - Service test ke liye stop karo
   - Warning: Notification gayab ho jayegi
   - Restart karne ke liye phir Start button dabao

### Notification Permissions
- Current permission status dikhata hai
- Agar disabled hai to **"Open Notification Settings"** button se settings khol sakte ho

---

## ✅ Expected Behavior

| Action | Expected Result |
|--------|----------------|
| App open karo | Notification automatically appear |
| Task add karo | Notification count update (instant) |
| Task complete karo | Notification count update (instant) |
| Swipe notification | Notification stays (no dismiss) |
| Phone reboot | Service auto-restart after boot |
| App force-stop | Service stops (restart app to resume) |
| Background app | Notification visible rahegi |

---

## 🚨 Common Issues & Fixes

### Issue 1: "Service shows ❌ Stopped in debug screen"
**Fix**: 
- Debug screen me **"Start/Restart Service"** button tap karo
- Pull down notification tray to verify

### Issue 2: "Notification appears but gets swiped away"
**Fix**:
- Service running nahi hai
- Debug screen check karo
- Agar running dikha raha hai but still swipeable = App reinstall karo (clean build)

### Issue 3: "Notification Permission Disabled"
**Fix**:
- Settings → Apps → Preamble → Notifications → Enable
- Ya debug screen se **"Open Notification Settings"** use karo

### Issue 4: "Notification disappears after some time"
**Possible Causes**:
- Battery optimization killing service
- **Fix**: Settings → Battery → Preamble → **Unrestricted** mode

### Issue 5: "Service keeps stopping"
**Fix**:
- Background restriction check karo
- Settings → Apps → Preamble → Battery → **No restrictions**
- Some manufacturers (Xiaomi, Oppo, Vivo): Additional "Autostart" permission chahiye

---

## 🔄 Service Lifecycle

```
App Launch
    ↓
PreambleApplication.onCreate()
    ↓
TaskNotificationService.start()
    ↓
Service runs in foreground
    ↓
Notification appears (permanent)
    ↓
Observes task changes (auto-updates)
    ↓
Stays active until:
    - App force-stopped
    - Device reboots (then auto-restarts)
    - System kills due to memory pressure
```

---

## 📊 Debug Checklist

Before asking for help, verify:

- [ ] Debug screen shows **✅ Running**
- [ ] Notification permissions **enabled** in phone settings
- [ ] Battery optimization set to **unrestricted**
- [ ] Notification tray me "Preamble" notification visible
- [ ] Swipe test kiya - notification **dismissed nahi** hui
- [ ] Task add/complete kiya - notification **instantly updated**
- [ ] App background me daalne ke baad notification **visible** rahi

---

## 🎓 Technical Details (Optional)

**Service Type**: `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`  
**Notification Channel**: `preamble_tasks_persistent`  
**Update Method**: Reactive `Flow` observation (realtime)  
**Fallback**: 30-second periodic refresh  
**Boot Restart**: `RECEIVE_BOOT_COMPLETED` broadcast receiver

---

## 💡 Tips

1. **First Time Setup**: App install ke baad notification permission manually grant karna pad sakta hai
2. **Realtime Updates**: Service automatically 0-delay me notification update karti hai (Flow-based)
3. **Battery Efficient**: Service sirf notification show karti hai, heavy processing nahi
4. **No Internet Needed**: Service locally chalta hai, offline kaam karta hai

---

**Ab app use karo aur notification swipe test try karo! 🚀**
