# Overlay Integration Guide for BulksendActivity

## Step 1: Add Manager to Activity

```kotlin
class BulksendActivity : ComponentActivity() {
    
    private lateinit var overlayManager: CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        // Set callbacks
        overlayManager.setOnStartCallback {
            // Resume campaign
            resumeCampaignSending()
        }
        
        overlayManager.setOnStopCallback {
            // Pause campaign
            pauseCampaignSending()
        }
        
        setContent {
            WhatsAppCampaignTheme {
                CampaignManagerScreen()
            }
        }
    }
}
```

## Step 2: Start Campaign with Overlay

```kotlin
fun startCampaign(contacts: List<Contact>) {
    // Start overlay
    overlayManager.startCampaignWithOverlay(contacts.size)
    
    // Start sending messages
    contacts.forEachIndexed { index, contact ->
        // Check if paused
        if (overlayManager.isPaused()) {
            // Wait until resumed
            while (overlayManager.isPaused()) {
                delay(500)
            }
        }
        
        // Send message
        sendMessage(contact)
        
        // Update overlay progress
        overlayManager.updateProgress(index + 1, contacts.size)
    }
    
    // Stop overlay when done
    overlayManager.stopCampaign()
}
```

## Step 3: Handle Pause/Resume

```kotlin
private var isPausedByUser = false

fun pauseCampaignSending() {
    isPausedByUser = true
    // Your pause logic
}

fun resumeCampaignSending() {
    isPausedByUser = false
    // Your resume logic
}
```

## Complete Example

```kotlin
@Composable
fun CampaignManagerScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? BulksendActivity
    
    Button(onClick = {
        scope.launch {
            // Get contacts
            val contacts = getSelectedContacts()
            
            // Start campaign with overlay
            activity?.overlayManager?.startCampaignWithOverlay(contacts.size)
            
            // Send messages
            contacts.forEachIndexed { index, contact ->
                // Check pause status
                while (activity?.overlayManager?.isPaused() == true) {
                    delay(500)
                }
                
                // Send message
                sendWhatsAppMessage(contact)
                
                // Update progress
                activity?.overlayManager?.updateProgress(index + 1, contacts.size)
                
                delay(2000) // Delay between messages
            }
            
            // Campaign complete
            activity?.overlayManager?.stopCampaign()
            Toast.makeText(context, "Campaign completed!", Toast.LENGTH_SHORT).show()
        }
    }) {
        Text("Start Campaign")
    }
}
```

## Features:
- ✅ Automatic overlay start/stop
- ✅ Real-time progress updates
- ✅ Pause/Resume from overlay
- ✅ Lifecycle aware (auto cleanup)
- ✅ Easy integration
