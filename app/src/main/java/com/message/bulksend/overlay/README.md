# Overlay Service Usage

## How to Use:

### 1. Start Overlay
```kotlin
// Start overlay with initial counts
OverlayHelper.startOverlay(context, sent = 0, total = 100)
```

### 2. Update Progress
```kotlin
// Update progress during campaign
OverlayHelper.updateOverlay(context, sent = 25, total = 100)
```

### 3. Stop Overlay
```kotlin
// Stop overlay when done
OverlayHelper.stopOverlay(context)
```

### 4. Check Permission
```kotlin
if (!OverlayHelper.hasOverlayPermission(context)) {
    OverlayHelper.requestOverlayPermission(context)
}
```

### 5. Listen to Control Actions
```kotlin
// In your Activity/Service
private val overlayReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.getStringExtra(OverlayService.EXTRA_ACTION)
        when (action) {
            "start" -> resumeCampaign()
            "stop" -> pauseCampaign()
        }
    }
}

// Register receiver
registerReceiver(overlayReceiver, IntentFilter(OverlayService.ACTION_CONTROL))

// Don't forget to unregister
unregisterReceiver(overlayReceiver)
```

## Features:
- ✅ Transparent floating card
- ✅ Shows sent/remaining count
- ✅ Start/Stop button
- ✅ Close button
- ✅ Draggable (can be enhanced)
- ✅ Works on all Android versions
