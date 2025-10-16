# BulktextActivity Overlay Implementation Plan

## Changes Required (Same as BulksendActivity)

### 1. Add OverlayManager to Class
```kotlin
class BulktextActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        setContent {
            WhatsAppCampaignTheme {
                TextCampaignManagerScreen()
            }
        }
    }
}
```

### 2. Remove Component Imports
```kotlin
// Remove these:
import com.message.bulksend.components.ResumeButton
import com.message.bulksend.components.ResumeConfirmationDialog
import com.message.bulksend.components.StopButton
```

### 3. Add State Variable
```kotlin
var showOverlayPermissionDialog by remember { mutableStateOf(false) }
```

### 4. Remove State Variable
```kotlin
// Remove:
var showResumeConfirmation by remember { mutableStateOf(false) }
```

### 5. Add Overlay Callbacks in LaunchedEffect
```kotlin
LaunchedEffect(Unit) {
    val activity = context as? BulktextActivity
    activity?.overlayManager?.setOnStartCallback {
        // Resume campaign
    }
    
    activity?.overlayManager?.setOnStopCallback {
        // Pause campaign
    }
}
```

### 6. Add Overlay Permission Check
```kotlin
// Before accessibility check
if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
    showOverlayPermissionDialog = true
    return@CampaignSummaryCard
}
```

### 7. Add Overlay Permission Dialog
```kotlin
if (showOverlayPermissionDialog) {
    AlertDialog(
        // Same dialog as BulksendActivity
    )
}
```

### 8. Remove UI Components
```kotlin
// Remove these items:
- ResumeButton
- StopButton  
- ResumeConfirmationDialog
```

### 9. Add Overlay Integration in Campaign Launch
```kotlin
// Start overlay with campaign
(context as? BulktextActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

// Check pause in sending loop
while ((context as? BulktextActivity)?.overlayManager?.isPaused() == true) {
    delay(500)
}

// Update progress
(context as? BulktextActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
```

## Implementation Steps

1. ✅ Read BulktextActivity.kt
2. ⏳ Add overlayManager to class
3. ⏳ Remove component imports
4. ⏳ Add/remove state variables
5. ⏳ Add overlay callbacks
6. ⏳ Add permission check
7. ⏳ Add permission dialog
8. ⏳ Remove Stop/Resume buttons
9. ⏳ Add overlay integration
10. ⏳ Build & test

## Files to Modify
- `BulktextActivity.kt` - Main implementation file

---
**Status:** Planning Complete, Ready for Implementation
