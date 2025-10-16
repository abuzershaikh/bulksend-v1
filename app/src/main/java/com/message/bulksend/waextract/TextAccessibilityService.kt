package com.spreadsheet.testing.waextract

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import java.util.regex.Pattern

class TextAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var extractionRunnable: Runnable? = null
    private val DEBOUNCE_DELAY_MS = 350L
    private val processedMessages = HashSet<String>()

    companion object {
        const val ACTION_TEXT_CAPTURED = "com.spreadsheet.testing.TEXT_CAPTURED"
        const val EXTRA_CAPTURED_TEXT = "CAPTURED_TEXT"
        private const val TAG = "WhatsAppAccessibility"
        private const val PREFS_NAME = "CapturedTextPrefs"
        private const val PREFS_KEY = "UniqueTexts"
        
        val extractedTexts = HashSet<String>()
        val extractedNumbers = HashSet<String>()
    }

    private val commonUiWords = setOf(
        "home", "settings", "back", "share", "copy", "paste", "clear all", "screenshot",
        "app info", "lock task", "enable accessibility service", "copy text", "clear text",
        "extracted text:", "waiting for text from other apps...", "search", "more options"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            val packageName = event.packageName?.toString()
            
            // Only process WhatsApp and WhatsApp Business
            if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
                Log.d(TAG, "📱 WhatsApp event detected: $packageName")
                
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
            } else {
                // Clear data when not in WhatsApp
                processedMessages.clear()
                extractedTexts.clear()
                extractedNumbers.clear()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onAccessibilityEvent: ${e.message}", e)
        }
    }

    private fun extractTextsFromNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                val text = childNode.text?.toString()
                if (text != null && !processedMessages.contains(text)) {
                    processedMessages.add(text)
                    
                    if (!extractedTexts.contains(text)) {
                        extractedTexts.add(text)
                        Log.d(TAG, "📝 Extracted text: $text")
                        
                        // Extract phone numbers from text
                        val numbers = findNumbersInText(text)
                        if (numbers.isNotEmpty()) {
                            Log.d(TAG, "📞 Found numbers: $numbers")
                            extractedNumbers.addAll(numbers)
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
            saveText(numbersText)
            broadcastText(numbersText)
        }
    }

    private fun filterIntelligentText(text: String): String {
        return text.lines()
            .map { it.trim() }
            .filter { line ->
                line.length > 2 && !commonUiWords.contains(line.lowercase(Locale.ROOT)) && !line.startsWith("ID:")
            }
            .joinToString("\n")
    }

    private fun isAlreadySaved(text: String): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTexts = prefs.getStringSet(PREFS_KEY, emptySet()) ?: emptySet()
        return savedTexts.contains(text)
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
        Log.e(TAG, "❌ Service Interrupted!")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Service Connected!")
        
        val serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 500L
        }
        setServiceInfo(serviceInfo)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        extractionRunnable?.let { handler.removeCallbacks(it) }
        Log.e(TAG, "❌ Service Unbound/Stopped!")
        return super.onUnbind(intent)
    }
}
