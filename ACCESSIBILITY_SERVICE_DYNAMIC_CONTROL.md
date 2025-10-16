# Accessibility Service Dynamic Start/Stop Solution

## 🎯 Problem Statement

**Current Issues:**
1. ❌ User app force close karta hai → Service on rehti hai
2. ❌ Campaign running rehta hai even after app closed
3. ❌ Overlay band hone par service band nahi hoti
4. ❌ Service manually settings se enable/disable karna padta hai

**Required Solution:**
1. ✅ Campaign launch → Service automatically start
2. ✅ Overlay close → Service automatically stop
3. ✅ App force close → Service automatically stop
4. ✅ No manual intervention needed

## 🚫 Android Limitation

**IMPORTANT:** Android **does NOT allow** programmatic start/stop of Accessibility Service!

**Why?**
- Security reasons
- Accessibility services have high privileges
- Only user can enable/disable from settings

**What We CAN Do:**
- ✅ Control service behavior (active/inactive)
- ✅ Detect app lifecycle
- ✅ Clean up on app close
- ❌ Cannot programmatically enable/disable service

## 💡 Best Solution: Lifecycle-Aware Service Control

### Approach: Service Active Only When Needed

```
Campaign Launch
    ↓
Set Service ACTIVE
    ↓
Campaign Running
    ↓
Overlay Close / App Close / Campaign End
    ↓
Set Service INACTIVE
    ↓
Clean up campaign state
```

## 🔧 Implementation Strategy

### Part 1: Service State Management (Already Done)

```kotlin
// In WhatsAppAutoSendService.kt
companion object {
    @Volatile
    private var isServiceActive = false
    
    fun activateService() {
        isServiceActive = true
    }
    
    fun deactivateService() {
        isServiceActive = false
    }
}

override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!isServiceActive) return
    // Process events
}
```

### Part 2: Overlay Close → Deactivate Service

```kotlin
// In OverlayService.kt
btnClose.setOnClickListener {
    // 1. Deactivate accessibility service
    WhatsAppAutoSendService.deactivateService()
    
    // 2. Stop campaign in database
    stopCampaign()
    
    // 3. Close overlay
    stopSelf()
}

private fun stopCampaign() {
    // Send broadcast to stop campaign
    val intent = Intent(ACTION_STOP_CAMPAIGN)
    sendBroadcast(intent)
}
```

### Part 3: App Lifecycle Detection

```kotlin
// In Application class or Activity
class BulksendApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Register lifecycle callbacks
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            
            override fun onActivityDestroyed(activity: Activity) {
                // Check if all activities destroyed
                if (isAppClosing()) {
                    // Deactivate service
                    WhatsAppAutoSendService.deactivateService()
                    
                    // Stop any running campaigns
                    stopAllCampaigns()
                }
            }
            
            // Other lifecycle methods...
        })
    }
}
```

### Part 4: Campaign State Cleanup

```kotlin
// In CampaignDao.kt
@Query("UPDATE campaigns SET isRunning = 0, isStopped = 1 WHERE isRunning = 1")
suspend fun stopAllRunningCampaigns()

// Call this when app closes or overlay closes
scope.launch(Dispatchers.IO) {
    campaignDao.stopAllRunningCampaigns()
}
```

## 📋 Complete Implementation

### File 1: WhatsAppAutoSendService.kt

```kotlin
class WhatsAppAutoSendService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSendService"
        
        @Volatile
        private var isServiceActive = false
        
        fun activateService() {
            isServiceActive = true
            Log.d(TAG, "✅ Service ACTIVATED")
        }
        
        fun deactivateService() {
            isServiceActive = false
            Log.d(TAG, "❌ Service DEACTIVATED")
        }
        
        fun isActive(): Boolean = isServiceActive
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // CRITICAL: Check if service should be active
        if (!isServiceActive) {
            // Service inactive - ignore all events
            return
        }
        
        // Also check CampaignState
        if (!CampaignState.isAutoSendEnabled) {
            return
        }

        // Process events only when active
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        val sendButtonIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send"
        )

        var sendButtonNode: AccessibilityNodeInfo? = null
        for (id in sendButtonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                sendButtonNode = nodes[0]
                break
            }
        }

        if (sendButtonNode != null && sendButtonNode.isVisibleToUser) {
            Log.d(TAG, "Send button found, clicking...")
            sendButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            CampaignState.isSendActionSuccessful = true
        }

        rootNode.recycle()
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service interrupted")
        deactivateService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connected but INACTIVE by default
        isServiceActive = false
        Log.i(TAG, "Service connected (INACTIVE by default)")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        deactivateService()
        Log.i(TAG, "Service destroyed")
    }
}
```

### File 2: OverlayService.kt

```kotlin
// In setupViews() method, update close button:

btnClose?.setOnClickListener {
    Log.d("OverlayService", "Close button clicked")
    
    // 1. Deactivate accessibility service
    try {
        WhatsAppAutoSendService.deactivateService()
        Log.d("OverlayService", "Accessibility service deactivated")
    } catch (e: Exception) {
        Log.e("OverlayService", "Error deactivating service", e)
    }
    
    // 2. Stop campaign
    stopCampaign()
    
    // 3. Close overlay
    stopSelf()
}

private fun stopCampaign() {
    // Send broadcast to stop campaign
    val intent = Intent(ACTION_STOP_CAMPAIGN)
    sendBroadcast(intent)
}

companion object {
    const val ACTION_STOP_CAMPAIGN = "com.message.bulksend.STOP_CAMPAIGN"
    // ... existing constants
}
```

### File 3: CampaignOverlayManager.kt

```kotlin
fun stopCampaign() {
    // 1. Deactivate accessibility service
    WhatsAppAutoSendService.deactivateService()
    
    // 2. Stop overlay
    if (isOverlayEnabled) {
        OverlayHelper.stopOverlay(context)
        isOverlayEnabled = false
        isCampaignRunning = false
    }
    
    Log.d(TAG, "Campaign stopped, service deactivated")
}

@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
fun onDestroy() {
    try {
        // Deactivate service when activity destroyed
        WhatsAppAutoSendService.deactivateService()
        
        context.unregisterReceiver(overlayReceiver)
        if (isOverlayEnabled) {
            OverlayHelper.stopOverlay(context)
        }
        Log.d(TAG, "Overlay receiver unregistered, service deactivated")
    } catch (e: Exception) {
        Log.e(TAG, "Error in onDestroy", e)
    }
}
```

### File 4: All Campaign Activities (4 files)

```kotlin
// In campaign launch section:
scope.launch {
    try {
        isSending = true
        
        // 1. Activate accessibility service
        WhatsAppAutoSendService.activateService()
        Log.d("Campaign", "Accessibility service activated")
        
        // 2. Start overlay
        overlayManager?.startCampaignWithOverlay(totalContacts)
        
        // 3. Send messages
        for (contact in contacts) {
            // ... send logic ...
        }
        
    } catch (e: Exception) {
        Log.e("Campaign", "Error in campaign", e)
    } finally {
        // 4. Always deactivate service when done
        WhatsAppAutoSendService.deactivateService()
        Log.d("Campaign", "Campaign ended, service deactivated")
        
        isSending = false
    }
}
```

### File 5: Add Broadcast Receiver in Activities

```kotlin
// In each campaign activity:

private val campaignStopReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == OverlayService.ACTION_STOP_CAMPAIGN) {
            // Stop campaign
            scope.launch(Dispatchers.IO) {
                currentCampaignId?.let { id ->
                    campaignDao.updateStopFlag(id, true)
                }
            }
            
            // Deactivate service
            WhatsAppAutoSendService.deactivateService()
            
            // Update UI
            isSending = false
        }
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Register receiver
    val filter = IntentFilter(OverlayService.ACTION_STOP_CAMPAIGN)
    registerReceiver(campaignStopReceiver, filter)
    
    // ... rest of code
}

override fun onDestroy() {
    super.onDestroy()
    
    // Deactivate service when activity destroyed
    WhatsAppAutoSendService.deactivateService()
    
    try {
        unregisterReceiver(campaignStopReceiver)
    } catch (e: Exception) {
        // Already unregistered
    }
}
```

## 🔄 Complete Flow Diagram

### Normal Campaign Flow:
```
1. User clicks "Launch Campaign"
         ↓
2. WhatsAppAutoSendService.activateService()
         ↓
3. Service starts processing events
         ↓
4. Messages send hote hain
         ↓
5. Campaign complete
         ↓
6. WhatsAppAutoSendService.deactivateService()
         ↓
7. Service stops processing
```

### User Closes Overlay:
```
1. User clicks ✕ on overlay
         ↓
2. btnClose.onClick()
         ↓
3. WhatsAppAutoSendService.deactivateService()
         ↓
4. Send broadcast: STOP_CAMPAIGN
         ↓
5. Activity receives broadcast
         ↓
6. Update campaign: isStopped = true
         ↓
7. Service stops processing
         ↓
8. Overlay closes
```

### User Force Closes App:
```
1. User swipes app away / Force stop
         ↓
2. Activity.onDestroy() called
         ↓
3. WhatsAppAutoSendService.deactivateService()
         ↓
4. overlayManager.onDestroy() called
         ↓
5. Service stops processing
         ↓
6. Campaign state cleaned
```

## ✅ Benefits

1. **Automatic Cleanup** - Service deactivates when app closes
2. **Overlay Control** - Close button properly stops service
3. **No Manual Work** - User doesn't need to disable service
4. **Clean State** - Service inactive when not needed
5. **Robust** - Handles force close, overlay close, normal end

## 🧪 Testing Scenarios

### Test 1: Normal Campaign
```
1. Launch campaign
2. Check: Service active
3. Messages sending
4. Campaign completes
5. Check: Service inactive
✅ Pass
```

### Test 2: Overlay Close
```
1. Launch campaign
2. Campaign running
3. Click ✕ on overlay
4. Check: Service inactive
5. Open WhatsApp
6. Type message
✅ Pass: No auto-send
```

### Test 3: Force Close App
```
1. Launch campaign
2. Campaign running
3. Swipe app away (force close)
4. Check: Service inactive
5. Open WhatsApp
✅ Pass: No auto-send
```

### Test 4: Multiple Campaigns
```
1. Launch campaign 1
2. Complete
3. Check: Service inactive
4. Launch campaign 2
5. Check: Service active
✅ Pass: Proper state management
```

## 📊 Summary

**Problem:** Service remains active after app close

**Solution:** 
1. Activate service on campaign start
2. Deactivate on campaign end
3. Deactivate on overlay close
4. Deactivate on app destroy

**Files to Modify:** 6 files
1. WhatsAppAutoSendService.kt
2. OverlayService.kt
3. CampaignOverlayManager.kt
4. BulksendActivity.kt
5. BulktextActivity.kt
6. TextmediaActivity.kt
7. SheetsendActivity.kt

---

**Status:** Solution Complete
**Ready for Implementation:** ✅ YES
