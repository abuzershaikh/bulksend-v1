package com.message.bulksend.bulksend

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import java.util.regex.Pattern

class WhatsAppAutoSendService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var extractionRunnable: Runnable? = null
    private val DEBOUNCE_DELAY_MS = 200L

    companion object {
        private const val TAG = "AutoSendService"
        const val ACTION_TEXT_CAPTURED = "com.message.bulksend.TEXT_CAPTURED"
        const val EXTRA_CAPTURED_TEXT = "CAPTURED_TEXT"
        private const val PREFS_NAME = "CapturedTextPrefs"
        private const val PREFS_KEY = "UniqueTexts"
        
        @Volatile
        private var isServiceActive = false
        
        @Volatile
        private var isContactExtractionEnabled = false
        
        val extractedTexts = HashSet<String>()
        val extractedNumbers = HashSet<String>()
        
        fun activateService() {
            isServiceActive = true
            Log.d(TAG, "✅ Service ACTIVATED")
        }
        
        fun deactivateService() {
            isServiceActive = false
            Log.d(TAG, "❌ Service DEACTIVATED")
        }
        
        fun isActive(): Boolean = isServiceActive
        
        fun enableContactExtraction() {
            isContactExtractionEnabled = true
            // Clear previous data when enabling
            extractedTexts.clear()
            extractedNumbers.clear()
            Log.d(TAG, "✅ Contact Extraction ENABLED (data cleared)")
        }
        
        fun disableContactExtraction() {
            isContactExtractionEnabled = false
            Log.d(TAG, "❌ Contact Extraction DISABLED")
        }
        
        fun isContactExtractionActive(): Boolean = isContactExtractionEnabled
        
        fun clearExtractedData() {
            extractedTexts.clear()
            extractedNumbers.clear()
            Log.d(TAG, "🗑️ Extracted data cleared")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString()
        
        // Log all events for debugging
        Log.d(TAG, "📱 Event received from: $packageName, type: ${event.eventType}")
        
        // Only process WhatsApp and WhatsApp Business
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }
        
        Log.d(TAG, "✅ WhatsApp event detected, extraction enabled: $isContactExtractionEnabled")
        
        // Handle Contact Extraction (if enabled)
        if (isContactExtractionEnabled) {
            Log.d(TAG, "🔍 Starting contact extraction...")
            handleContactExtraction(event)
        }
        
        // Handle Auto Send (if enabled and service active)
        if (isServiceActive && CampaignState.isAutoSendEnabled) {
            handleAutoSend(event)
        }
    }
    
    private fun handleContactExtraction(event: AccessibilityEvent) {
        try {
            extractionRunnable?.let { handler.removeCallbacks(it) }
            
            extractionRunnable = Runnable {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    extractTextsFromNode(rootNode)
                    rootNode.recycle()
                    
                    // Broadcast extracted numbers
                    if (extractedNumbers.isNotEmpty()) {
                        Log.d(TAG, "✅ Extracted ${extractedNumbers.size} numbers")
                        broadcastExtractedNumbers()
                    }
                }
            }
            handler.postDelayed(extractionRunnable!!, DEBOUNCE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in contact extraction: ${e.message}", e)
        }
    }
    
    private fun handleAutoSend(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
            Log.d(TAG, "Send button mila, click kar raha hu.")
            sendButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            CampaignState.isSendActionSuccessful = true
        } else {
            Log.d(TAG, "Send button nahi mila ya dikhai nahi de raha.")
        }

        rootNode.recycle()
    }
    
    private fun extractTextsFromNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                val text = childNode.text?.toString()
                if (text != null && text.isNotBlank()) {
                    // No need to check processedMessages - let HashSets handle duplicates
                    if (!extractedTexts.contains(text)) {
                        extractedTexts.add(text)
                        Log.d(TAG, "📝 Extracted text: $text")
                    }
                    
                    // Always try to extract phone numbers (HashSet will handle duplicates)
                    val numbers = findNumbersInText(text)
                    if (numbers.isNotEmpty()) {
                        val newNumbers = numbers.filter { !extractedNumbers.contains(it) }
                        if (newNumbers.isNotEmpty()) {
                            Log.d(TAG, "📞 Found new numbers: $newNumbers")
                            extractedNumbers.addAll(newNumbers)
                        }
                    }
                }
                
                // Recursive call to process child nodes
                extractTextsFromNode(childNode)
                childNode.recycle()
            }
        }
    }
    
    private fun findNumbersInText(text: String): ArrayList<String> {
        val numbers = ArrayList<String>()
        // Pattern to match phone numbers with country code (MUST start with +)
        val pattern = Pattern.compile("(?!\\+0)\\+\\d+(?:[-\\s(]*\\d+[\\s)-]*)+")
        val lines = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
        
        for (line in lines) {
            // Only process lines that contain +
            if (!line.contains("+")) continue
            
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                var phoneNumber = matcher.group()
                // Remove spaces and symbols from the phone number
                phoneNumber = phoneNumber.replace("[\\s()-]".toRegex(), "")
                
                // Double check: ONLY add if starts with +
                if (phoneNumber.startsWith("+") && phoneNumber.length >= 8) {
                    numbers.add(phoneNumber)
                    Log.d(TAG, "✅ Extracted number: $phoneNumber")
                }
            }
        }
        return numbers
    }
    
    private fun broadcastExtractedNumbers() {
        val numbersText = extractedNumbers.joinToString("\n")
        if (numbersText.isNotBlank()) {
            Log.d(TAG, "📤 Broadcasting ${extractedNumbers.size} numbers")
            saveText(numbersText)
            broadcastText(numbersText)
        } else {
            Log.d(TAG, "⚠️ No numbers to broadcast")
        }
    }
    
    private fun saveText(text: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTexts = prefs.getStringSet(PREFS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        savedTexts.add(text)
        prefs.edit().putStringSet(PREFS_KEY, savedTexts).apply()
    }

    private fun broadcastText(text: String) {
        val intent = Intent(ACTION_TEXT_CAPTURED).putExtra(EXTRA_CAPTURED_TEXT, text)
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility service interrupt ho gayi.")
        deactivateService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connected but INACTIVE by default
        isServiceActive = false
        isContactExtractionEnabled = false
        Log.i(TAG, "✅ Accessibility service connected (Both features INACTIVE by default)")
        Log.i(TAG, "Auto-send enabled status: ${CampaignState.isAutoSendEnabled}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        extractionRunnable?.let { handler.removeCallbacks(it) }
        deactivateService()
        disableContactExtraction()
        Log.i(TAG, "Service destroyed, all features deactivated")
    }
}
