# BulktextActivity Overlay Implementation - Quick Summary

## ✅ Changes Already Done

1. ✅ Added `overlayManager` to BulktextActivity class
2. ✅ Removed component imports (StopButton, ResumeButton, etc.)

## ⏳ Remaining Changes (Same Pattern as BulksendActivity)

### Find and Apply These Changes:

### 1. State Variables Section
**Find:** State variables declaration area
**Add:**
```kotlin
var showOverlayPermissionDialog by remember { mutableStateOf(false) }
```

**Remove (if exists):**
```kotlin
var showResumeConfirmation by remember { mutableStateOf(false) }
```

### 2. Add Overlay Callbacks
**Find:** After state variables, in LaunchedEffect
**Add:**
```kotlin
// Setup overlay callbacks
LaunchedEffect(Unit) {
    val activity = context as? BulktextActivity
    activity?.overlayManager?.setOnStartCallback {
        android.util.Log.d("BulktextActivity", "Campaign resumed from overlay")
    }
    
    activity?.overlayManager?.setOnStopCallback {
        android.util.Log.d("BulktextActivity", "Campaign paused from overlay")
    }
}
```

### 3. Permission Check Before Campaign Launch
**Find:** Campaign launch validation (before accessibility check)
**Add:**
```kotlin
// Check overlay permission first
if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
    showOverlayPermissionDialog = true
    return@CampaignSummaryCard
}
```

### 4. Remove Stop/Resume Buttons from UI
**Find and Remove:**
```kotlin
item {
    ResumeButton(...)
}

item {
    StopButton(...)
}
```

**Replace with:**
```kotlin
// Stop/Resume buttons removed - now controlled via overlay only
```

### 5. Remove Resume Confirmation Dialog
**Find and Remove:**
```kotlin
ResumeConfirmationDialog(
    showDialog = showResumeConfirmation,
    ...
)
```

**Replace with:**
```kotlin
// ResumeConfirmationDialog removed - resume handled via overlay
```

### 6. Add Overlay Permission Dialog
**Find:** Where other dialogs are (like AccessibilityPermissionDialog)
**Add:**
```kotlin
// Overlay Permission Dialog
if (showOverlayPermissionDialog) {
    AlertDialog(
        onDismissRequest = { showOverlayPermissionDialog = false },
        icon = { 
            Icon(
                Icons.Outlined.Layers, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            ) 
        },
        title = { 
            Text(
                "Overlay Permission Required",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Overlay permission is required for campaign control.",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "The overlay allows you to pause and resume campaigns without opening the app:",
                    fontSize = 14.sp
                )
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", fontWeight = FontWeight.Bold)
                        Text("Overlay appears on screen when campaign is running", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", fontWeight = FontWeight.Bold)
                        Text("Control campaign with Stop/Start button", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", fontWeight = FontWeight.Bold)
                        Text("View real-time progress", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Enable 'Display over other apps' permission in Settings.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showOverlayPermissionDialog = false
                    com.message.bulksend.overlay.OverlayHelper.requestOverlayPermission(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("I Agree")
            }
        },
        dismissButton = {
            TextButton(onClick = { showOverlayPermissionDialog = false }) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    )
}
```

### 7. Integrate Overlay in Campaign Launch
**Find:** Where campaign starts (`scope.launch { isSending = true ...`)
**Add after campaign creation:**
```kotlin
// Start overlay with campaign
(context as? BulktextActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)
```

**In sending loop, add pause check:**
```kotlin
for (contactStatus in contactsToSend) {
    // Check if paused by overlay
    while ((context as? BulktextActivity)?.overlayManager?.isPaused() == true) {
        delay(500)
    }
    
    // ... rest of sending logic
}
```

**Update progress:**
```kotlin
// Update overlay progress
(context as? BulktextActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
```

## Quick Reference - Files Modified

1. ✅ `BulktextActivity.kt` - Class definition updated
2. ✅ `BulktextActivity.kt` - Imports cleaned
3. ⏳ `BulktextActivity.kt` - State variables (add/remove)
4. ⏳ `BulktextActivity.kt` - Overlay callbacks
5. ⏳ `BulktextActivity.kt` - Permission check
6. ⏳ `BulktextActivity.kt` - Permission dialog
7. ⏳ `BulktextActivity.kt` - Remove buttons
8. ⏳ `BulktextActivity.kt` - Overlay integration

## Testing After Implementation

1. Open BulktextActivity
2. Setup text campaign
3. Click "Launch Campaign"
4. Overlay permission dialog should appear
5. Click "I Agree"
6. Enable permission in settings
7. Launch campaign again
8. Overlay should appear with Stop/Start button

---
**Status:** Partial - Class & Imports Done, Remaining Changes Documented
**Next:** Apply remaining changes following BulksendActivity pattern
