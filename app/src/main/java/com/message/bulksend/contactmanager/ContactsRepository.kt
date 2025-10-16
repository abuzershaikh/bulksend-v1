package com.message.bulksend.contactmanager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.ContactEntity
import com.message.bulksend.db.ContactGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ContactsRepository(private val context: Context) {

    private val contactGroupDao = AppDatabase.getInstance(context).contactGroupDao()

    fun loadGroups(): Flow<List<Group>> {
        return contactGroupDao.getAllGroups().map { dbGroups ->
            // Get current subscription status
            val subscriptionInfo = com.message.bulksend.utils.SubscriptionUtils.getLocalSubscriptionInfo(context)
            val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
            val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
            val isPremiumActive = subscriptionType == "premium" && !isExpired

            dbGroups
                .filter { dbGroup ->
                    // Show all groups if premium is active
                    // Show only non-premium groups if free plan
                    if (isPremiumActive) {
                        true  // Show all groups
                    } else {
                        !dbGroup.isPremiumGroup  // Hide premium groups on free plan
                    }
                }
                .map { dbGroup ->
                    Group(
                        id = dbGroup.id,
                        name = dbGroup.name,
                        contacts = dbGroup.contacts.map { dbContact ->
                            Contact(
                                name = dbContact.name,
                                number = dbContact.number,
                                isWhatsApp = dbContact.isWhatsApp
                            )
                        },
                        timestamp = dbGroup.timestamp,
                        isPremiumGroup = dbGroup.isPremiumGroup
                    )
                }
        }
    }

    suspend fun saveGroup(groupName: String, contacts: List<Contact>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Get subscription info
                val subscriptionInfo = com.message.bulksend.utils.SubscriptionUtils.getLocalSubscriptionInfo(context)
                val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                val currentGroups = subscriptionInfo["currentGroups"] as? Int ?: 0
                val currentContacts = subscriptionInfo["currentContacts"] as? Int ?: 0
                val contactsLimit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                val userEmail = subscriptionInfo["userEmail"] as? String ?: ""

                if (userEmail.isEmpty()) {
                    return@withContext Result.failure(Exception("User not logged in"))
                }

                // Check if premium is active
                val isPremiumActive = subscriptionType == "premium" && !isExpired

                // Limit contacts for FREE users only
                val limitedContacts = if (isPremiumActive) {
                    // Premium users: unlimited contacts
                    contacts
                } else {
                    // Free users: limit to 10 contacts total
                    val availableSlots = contactsLimit - currentContacts

                    if (availableSlots <= 0) {
                        return@withContext Result.failure(Exception(
                            "🚫 Contact limit reached!\n\n" +
                                    "Free plan: Maximum $contactsLimit contacts\n" +
                                    "Current: $currentContacts/$contactsLimit contacts\n\n" +
                                    "💎 Upgrade to Premium for unlimited contacts!"
                        ))
                    }

                    // Take only available slots
                    contacts.take(availableSlots)
                }

                if (limitedContacts.isEmpty()) {
                    return@withContext Result.failure(Exception(
                        "No contacts can be added!\n\n" +
                                "Contact limit already reached."
                    ))
                }

                // Save the group with limited contacts
                val contactEntities = limitedContacts.map {
                    ContactEntity(name = it.name, number = it.number, isWhatsApp = it.isWhatsApp)
                }

                // Mark if this is a premium group (created during premium plan)
                val isPremiumGroup = isPremiumActive

                val group = ContactGroup(
                    name = groupName,
                    contacts = contactEntities,
                    timestamp = System.currentTimeMillis(),
                    isPremiumGroup = isPremiumGroup  // Mark if created during premium
                )
                contactGroupDao.insertGroup(group)

                // Update local preferences
                val newContactCount = currentContacts + limitedContacts.size
                val newGroupCount = currentGroups + 1

                val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("current_contacts", newContactCount)
                    putInt("current_groups", newGroupCount)
                    apply()
                }

                android.util.Log.d("ContactsRepository", "✅ Group saved:")
                android.util.Log.d("ContactsRepository", "  Name: $groupName")
                android.util.Log.d("ContactsRepository", "  Contacts: ${limitedContacts.size}")
                android.util.Log.d("ContactsRepository", "  Premium Group: $isPremiumGroup")

                // Update Firebase in background (async, non-blocking)
                try {
                    val userManager = com.message.bulksend.auth.UserManager(context)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        userManager.updateContactCount(userEmail, newContactCount)
                        userManager.updateGroupCount(userEmail, newGroupCount)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ContactsRepository", "Firebase update failed (non-critical): ${e.message}")
                }

                // Success message
                val message = if (limitedContacts.size < contacts.size) {
                    "Group saved with ${limitedContacts.size} contacts!\n\n" +
                            "⚠️ ${contacts.size - limitedContacts.size} contacts were skipped due to free plan limit (max $contactsLimit).\n\n" +
                            "💎 Upgrade to Premium for unlimited contacts!"
                } else {
                    "✅ Group saved successfully! Added ${limitedContacts.size} contacts."
                }

                Result.success(message)

            } catch (e: Exception) {
                Log.e("ContactsRepository", "Error saving group", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        withContext(Dispatchers.IO) {
            // Function ka naam theek kiya gaya
            contactGroupDao.deleteGroup(groupId)
        }
    }

    fun parseCsv(context: Context, uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { line ->
                        if (line.trim().isNotEmpty()) {
                            // Skip header if it contains common header words
                            if (isFirstLine && (line.lowercase().contains("name") || line.lowercase().contains("phone") || line.lowercase().contains("number"))) {
                                isFirstLine = false
                                return@forEachLine
                            }
                            isFirstLine = false

                            // Handle both comma and semicolon separators
                            val separator = if (line.contains(";")) ";" else ","
                            val tokens = line.split(separator).map { it.trim().replace("\"", "") }

                            if (tokens.size >= 2) {
                                val name = tokens[0].trim()
                                val number = tokens[1].trim().replace(Regex("[^0-9+]"), "")
                                if (name.isNotBlank() && number.isNotBlank() && number.length >= 7) {
                                    contacts.add(Contact(name, number, isWhatsApp = false))
                                }
                            } else if (tokens.size == 1) {
                                // Handle single column with name and number in one field
                                val parts = tokens[0].split(Regex("\\s+"))
                                if (parts.size >= 2) {
                                    val number = parts.last().replace(Regex("[^0-9+]"), "")
                                    if (number.length >= 7) {
                                        val name = parts.dropLast(1).joinToString(" ")
                                        contacts.add(Contact(name.ifBlank { "Unknown" }, number, isWhatsApp = false))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "CSV parse karne mein error", e)
        }
        return contacts
    }

    fun parseVcf(context: Context, uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var currentName: String? = null
                    var currentNumber: String? = null
                    reader.forEachLine { line ->
                        when {
                            line.startsWith("FN:") -> currentName = line.substring(3).trim()
                            line.startsWith("TEL") -> {
                                currentNumber = line.substring(line.indexOf(":") + 1)
                                    .trim()
                                    .replace(Regex("[^0-9+]"), "")
                            }
                            line == "END:VCARD" -> {
                                if (currentName != null && currentNumber != null) {
                                    contacts.add(Contact(currentName!!, currentNumber!!, isWhatsApp = false))
                                }
                                currentName = null
                                currentNumber = null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "VCF parse karne mein error", e)
        }
        return contacts
    }

    fun parseXlsx(uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i)
                    val nameCell = row?.getCell(0)
                    val numberCell = row?.getCell(1)

                    val name = nameCell?.stringCellValue?.trim() ?: ""

                    val number = when (numberCell?.cellType) {
                        CellType.NUMERIC -> numberCell.numericCellValue.toLong().toString()
                        CellType.STRING -> numberCell.stringCellValue.trim().replace(Regex("[^0-9+]"), "")
                        else -> ""
                    }

                    if (name.isNotBlank() && number.isNotBlank()) {
                        contacts.add(Contact(name, number, isWhatsApp = false))
                    }
                }
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "XLSX file parse karne mein error", e)
        }
        return contacts
    }

    fun parseCommaSeparatedText(text: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        text.lines().forEach { line ->
            if (line.trim().isNotEmpty()) {
                // Handle both comma and semicolon separators
                val separator = if (line.contains(";")) ";" else ","
                val parts = line.split(separator).map { it.trim() }

                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val number = parts[1].trim().replace(Regex("[^0-9+]"), "")
                    if (name.isNotBlank() && number.isNotBlank() && number.length >= 7) {
                        contacts.add(Contact(name, number, isWhatsApp = false))
                    }
                } else if (parts.size == 1) {
                    // Try to extract name and number from single field
                    val singlePart = parts[0].trim()
                    val tokens = singlePart.split(Regex("\\s+"))
                    if (tokens.size >= 2) {
                        val number = tokens.last().replace(Regex("[^0-9+]"), "")
                        if (number.length >= 7) {
                            val name = tokens.dropLast(1).joinToString(" ")
                            contacts.add(Contact(name.ifBlank { "Unknown" }, number, isWhatsApp = false))
                        }
                    }
                }
            }
        }
        return contacts
    }

    @SuppressLint("Range")
    fun getWhatsAppContacts(): List<Contact> {
        val whatsappContacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.MIMETYPE
        )
        // Include both WhatsApp and WhatsApp Business MIME types
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            "vnd.android.cursor.item/vnd.com.whatsapp.profile",           // WhatsApp
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call",         // WhatsApp Voice
            "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"        // WhatsApp Business
        )

        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
                val contactId = it.getString(it.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                var number = ""

                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pCursor ->
                    if (pCursor.moveToFirst()) {
                        number = pCursor.getString(pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    }
                }
                if (name.isNotBlank() && number.isNotBlank()) {
                    whatsappContacts.add(Contact(name, number.replace(Regex("[^0-9+]"), ""), isWhatsApp = true))
                }
            }
        }
        return whatsappContacts.distinctBy { it.number }
    }

    fun getTotalContactsCount(): Int {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            return count
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Kul contacts ginne mein error", e)
            return 0
        }
    }

    suspend fun fetchFromGoogleSheets(sheetUrl: String): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        try {
            // Convert Google Sheets URL to CSV export URL
            val csvUrl = convertToCSVUrl(sheetUrl)

            val url = URL(csvUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { line ->
                        if (line.trim().isNotEmpty()) {
                            // Skip header if it contains common header words
                            if (isFirstLine && (line.lowercase().contains("name") || line.lowercase().contains("phone") || line.lowercase().contains("number"))) {
                                isFirstLine = false
                                return@forEachLine
                            }
                            isFirstLine = false

                            val tokens = line.split(",").map { it.trim().replace("\"", "") }
                            if (tokens.size >= 2) {
                                val name = tokens[0].trim()
                                val number = tokens[1].trim().replace(Regex("[^0-9+]"), "")
                                if (name.isNotBlank() && number.isNotBlank() && number.length >= 7) {
                                    contacts.add(Contact(name, number, isWhatsApp = false))
                                }
                            }
                        }
                    }
                }
            } else {
                throw Exception("Failed to fetch data from Google Sheets. Response code: ${connection.responseCode}")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Google Sheets se data fetch karne mein error", e)
            throw e
        }
        return@withContext contacts
    }

    private fun convertToCSVUrl(sheetUrl: String): String {
        return when {
            sheetUrl.contains("/edit") -> {
                sheetUrl.replace("/edit#gid=", "/export?format=csv&gid=")
                    .replace("/edit", "/export?format=csv")
            }
            sheetUrl.contains("docs.google.com/spreadsheets/d/") -> {
                val sheetId = sheetUrl.substringAfter("docs.google.com/spreadsheets/d/")
                    .substringBefore("/")
                "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
            }
            else -> sheetUrl
        }
    }
}

