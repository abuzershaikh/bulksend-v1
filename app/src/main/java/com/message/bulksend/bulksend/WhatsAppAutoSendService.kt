package com.message.bulksend.bulksend

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
            Log.d(TAG, "Auto-send service disabled hai, koi action nahi lenge.")
            return
        }

        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        // WhatsApp aur WhatsApp Business dono ke liye Send button ID
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
            Log.d("AutoSendService", "Send button mila, click kar raha hu.")
            sendButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Safal click par CampaignState ko update karein.
            CampaignState.isSendActionSuccessful = true
        } else {
            Log.d("AutoSendService", "Send button nahi mila ya dikhai nahi de raha.")
        }

        rootNode.recycle()
    }

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility service interrupt ho gayi.")
        deactivateService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connected but INACTIVE by default
        isServiceActive = false
        Log.i(TAG, "Accessibility service connect ho gayi hai (INACTIVE by default)")
        Log.i(TAG, "Auto-send enabled status: ${CampaignState.isAutoSendEnabled}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        deactivateService()
        Log.i(TAG, "Service destroyed, deactivated")
    }
}
