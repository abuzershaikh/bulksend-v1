# 🏗️ Overlay Architecture & Flow Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    BulksendActivity                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Campaign UI (Compose)                                │  │
│  │  - Campaign Setup                                     │  │
│  │  - Message Composer                                   │  │
│  │  - Launch Button                                      │  │
│  │  ❌ NO Stop/Resume Buttons                            │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           │ onCreate()                       │
│                           ↓                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  CampaignOverlayManager                               │  │
│  │  - Lifecycle Observer                                 │  │
│  │  - BroadcastReceiver                                  │  │
│  │  - isPaused() state                                   │  │
│  │  - Callbacks (onStart/onStop)                         │  │
│  └───────────────────────────────────────────────────────┘  │
│         │                    ↑                               │
│         │ startCampaign()    │ Broadcast                    │
│         │ updateProgress()   │ Intent                       │
│         ↓                    │                               │
└─────────────────────────────────────────────────────────────┘
          │                    │
          │                    │
          ↓                    │
┌─────────────────────────────────────────────────────────────┐
│                    OverlayHelper                            │
│  - hasOverlayPermission()                                   │
│  - requestOverlayPermission()                               │
│  - startOverlay()                                           │
│  - stopOverlay()                                            │
│  - updateOverlay()                                          │
└─────────────────────────────────────────────────────────────┘
          │
          │ startService()
          ↓
┌─────────────────────────────────────────────────────────────┐
│                    OverlayService                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Overlay UI (System Window)                          │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ 📊 Campaign Status                          [✕] │ │  │
│  │  ├─────────────────────────────────────────────────┤ │  │
│  │  │ Sent: 25 / 100                                  │ │  │
│  │  │ Remaining: 75                                   │ │  │
│  │  │ ─────────────────────────────────────────────── │ │  │
│  │  │ [        ■ Stop / ▶ Start        ]             │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           │ Button Click                     │
│                           ↓                                  │
│                    sendBroadcast()                           │
│                    (ACTION_CONTROL)                          │
└─────────────────────────────────────────────────────────────┘
```

## Campaign Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Campaign Lifecycle                       │
└─────────────────────────────────────────────────────────────┘

1. LAUNCH
   ┌──────────────┐
   │ User clicks  │
   │   Launch     │
   └──────┬───────┘
          │
          ↓
   ┌──────────────┐
   │ Initialize   │
   │  Campaign    │
   └──────┬───────┘
          │
          ↓
   ┌──────────────┐
   │ Start Overlay│
   │   Service    │
   └──────┬───────┘
          │
          ↓
   ┌──────────────┐
   │ Show Overlay │
   │ with Stop btn│
   └──────┬───────┘
          │
          ↓

2. SENDING
   ┌──────────────────────────────────┐
   │ For each contact:                │
   │                                  │
   │  ┌────────────────────────────┐  │
   │  │ Check isPaused()           │  │
   │  └────────┬───────────────────┘  │
   │           │                      │
   │           ↓                      │
   │  ┌────────────────────────────┐  │
   │  │ If paused: wait in loop    │  │
   │  │ If not: continue           │  │
   │  └────────┬───────────────────┘  │
   │           │                      │
   │           ↓                      │
   │  ┌────────────────────────────┐  │
   │  │ Send message               │  │
   │  └────────┬───────────────────┘  │
   │           │                      │
   │           ↓                      │
   │  ┌────────────────────────────┐  │
   │  │ Update progress            │  │
   │  └────────┬───────────────────┘  │
   │           │                      │
   │           ↓                      │
   │  ┌────────────────────────────┐  │
   │  │ Delay (5 sec)              │  │
   │  └────────────────────────────┘  │
   │                                  │
   └──────────────────────────────────┘
          │
          ↓

3. COMPLETE
   ┌──────────────┐
   │ All messages │
   │    sent      │
   └──────┬───────┘
          │
          ↓
   ┌──────────────┐
   │ Stop Overlay │
   │   Service    │
   └──────┬───────┘
          │
          ↓
   ┌──────────────┐
   │ Show Success │
   │   Message    │
   └──────────────┘
```

## Stop/Resume Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    User Clicks Stop                         │
└─────────────────────────────────────────────────────────────┘

   ┌──────────────┐
   │ User clicks  │
   │  ■ Stop btn  │
   └──────┬───────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ OverlayService               │
   │ - Toggle isRunning = false   │
   │ - Update button UI:          │
   │   Text: "▶ Start"            │
   │   Color: Green               │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ Send Broadcast Intent        │
   │ ACTION: "stop"               │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ CampaignOverlayManager       │
   │ - Receive broadcast          │
   │ - Set isPaused = true        │
   │ - Call onStopCallback()      │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ BulksendActivity             │
   │ - Sending loop checks        │
   │   isPaused()                 │
   │ - Enters wait loop:          │
   │   while (isPaused()) {       │
   │     delay(500)               │
   │   }                          │
   └──────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    User Clicks Start                        │
└─────────────────────────────────────────────────────────────┘

   ┌──────────────┐
   │ User clicks  │
   │ ▶ Start btn  │
   └──────┬───────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ OverlayService               │
   │ - Toggle isRunning = true    │
   │ - Update button UI:          │
   │   Text: "■ Stop"             │
   │   Color: Red                 │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ Send Broadcast Intent        │
   │ ACTION: "start"              │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ CampaignOverlayManager       │
   │ - Receive broadcast          │
   │ - Set isPaused = false       │
   │ - Call onStartCallback()     │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ BulksendActivity             │
   │ - isPaused() returns false   │
   │ - Exit wait loop             │
   │ - Continue sending messages  │
   └──────────────────────────────┘
```

## State Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Campaign States                          │
└─────────────────────────────────────────────────────────────┘

                    ┌──────────────┐
                    │   IDLE       │
                    │ (Not Started)│
                    └──────┬───────┘
                           │
                           │ Launch Campaign
                           ↓
                    ┌──────────────┐
                    │   RUNNING    │
                    │ (Sending)    │
                    │ Button: Stop │
                    └──────┬───────┘
                           │
                ┌──────────┼──────────┐
                │          │          │
        Stop    │          │          │ Complete
        Click   │          │          │
                ↓          │          ↓
         ┌──────────┐      │   ┌──────────┐
         │  PAUSED  │      │   │ COMPLETE │
         │ (Waiting)│      │   │ (Done)   │
         │Button:   │      │   └──────────┘
         │  Start   │      │
         └──────┬───┘      │
                │          │
        Start   │          │
        Click   │          │
                │          │
                └──────────┘

Legend:
- IDLE: Campaign not started
- RUNNING: Messages being sent, overlay shows "■ Stop"
- PAUSED: Campaign paused by user, overlay shows "▶ Start"
- COMPLETE: All messages sent, overlay closed
```

## Component Interaction

```
┌─────────────────────────────────────────────────────────────┐
│                    Component Interaction                    │
└─────────────────────────────────────────────────────────────┘

BulksendActivity          CampaignOverlayManager      OverlayService
      │                            │                         │
      │ onCreate()                 │                         │
      ├───────────────────────────>│                         │
      │                            │ register receiver       │
      │                            │                         │
      │ startCampaignWithOverlay() │                         │
      ├───────────────────────────>│                         │
      │                            │ startOverlay()          │
      │                            ├────────────────────────>│
      │                            │                         │ create UI
      │                            │                         │ show overlay
      │                            │                         │
      │                            │                         │ User clicks
      │                            │                         │ Stop button
      │                            │                         │
      │                            │<────────────────────────┤
      │                            │ broadcast: "stop"       │
      │<───────────────────────────┤                         │
      │ onStopCallback()           │                         │
      │ isPaused = true            │                         │
      │                            │                         │
      │ (wait in loop)             │                         │
      │                            │                         │
      │                            │                         │ User clicks
      │                            │                         │ Start button
      │                            │                         │
      │                            │<────────────────────────┤
      │                            │ broadcast: "start"      │
      │<───────────────────────────┤                         │
      │ onStartCallback()          │                         │
      │ isPaused = false           │                         │
      │                            │                         │
      │ (continue sending)         │                         │
      │                            │                         │
      │ updateProgress(25, 100)    │                         │
      ├───────────────────────────>│                         │
      │                            │ updateOverlay()         │
      │                            ├────────────────────────>│
      │                            │                         │ update UI
      │                            │                         │ "Sent: 25/100"
      │                            │                         │
      │ stopCampaign()             │                         │
      ├───────────────────────────>│                         │
      │                            │ stopOverlay()           │
      │                            ├────────────────────────>│
      │                            │                         │ close overlay
      │                            │                         │ stopSelf()
      │                            │                         │
      │ onDestroy()                │                         │
      ├───────────────────────────>│                         │
      │                            │ unregister receiver     │
      │                            │                         │
```

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Data Flow Diagram                        │
└─────────────────────────────────────────────────────────────┘

Campaign Data:
┌──────────────────────────────────────────────────────────┐
│ Campaign Object                                          │
│ - id: String                                             │
│ - campaignName: String                                   │
│ - totalContacts: Int                                     │
│ - sentCount: Int                                         │
│ - failedCount: Int                                       │
│ - contactStatuses: List<ContactStatus>                  │
│ - isStopped: Boolean                                     │
│ - isRunning: Boolean                                     │
└──────────────────────────────────────────────────────────┘
                    │
                    │ Save to DB
                    ↓
┌──────────────────────────────────────────────────────────┐
│ Room Database (AppDatabase)                             │
│ - CampaignDao                                            │
│   - upsertCampaign()                                     │
│   - getCampaignById()                                    │
│   - updateStopFlag()                                     │
└──────────────────────────────────────────────────────────┘

Progress Data:
┌──────────────────────────────────────────────────────────┐
│ Progress Update                                          │
│ - sent: Int (current count)                              │
│ - total: Int (total contacts)                            │
│ - remaining: Int (total - sent)                          │
└──────────────────────────────────────────────────────────┘
                    │
                    │ Update UI
                    ↓
┌──────────────────────────────────────────────────────────┐
│ Overlay Display                                          │
│ "Sent: 25 / 100"                                         │
│ "Remaining: 75"                                          │
└──────────────────────────────────────────────────────────┘

Control Data:
┌──────────────────────────────────────────────────────────┐
│ Broadcast Intent                                         │
│ - ACTION: "com.message.bulksend.OVERLAY_CONTROL"         │
│ - EXTRA_ACTION: "start" | "stop"                         │
└──────────────────────────────────────────────────────────┘
                    │
                    │ Broadcast
                    ↓
┌──────────────────────────────────────────────────────────┐
│ CampaignOverlayManager                                   │
│ - isPaused: Boolean                                      │
│ - isCampaignRunning: Boolean                             │
└──────────────────────────────────────────────────────────┘
```

## Permission Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Overlay Permission Flow                  │
└─────────────────────────────────────────────────────────────┘

   ┌──────────────────┐
   │ App starts       │
   └────────┬─────────┘
            │
            ↓
   ┌──────────────────┐
   │ Check permission │
   │ canDrawOverlays()│
   └────────┬─────────┘
            │
      ┌─────┴─────┐
      │           │
   Yes│           │No
      │           │
      ↓           ↓
┌──────────┐  ┌──────────────────┐
│ Continue │  │ Request          │
│          │  │ Permission       │
└──────────┘  └────────┬─────────┘
                       │
                       ↓
              ┌──────────────────┐
              │ Open Settings    │
              │ ACTION_MANAGE_   │
              │ OVERLAY_         │
              │ PERMISSION       │
              └────────┬─────────┘
                       │
                       ↓
              ┌──────────────────┐
              │ User grants      │
              │ permission       │
              └────────┬─────────┘
                       │
                       ↓
              ┌──────────────────┐
              │ Return to app    │
              │ Continue         │
              └──────────────────┘
```

---

**Architecture Complete! 🏗️**

Is diagram se aap easily samajh sakte ho ki:
1. Components kaise interact karte hain
2. Data kaise flow hota hai
3. Stop/Resume kaise kaam karta hai
4. States kaise change hoti hain

**Happy Coding! 💻**
