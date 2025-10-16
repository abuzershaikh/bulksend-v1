package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.message.bulksend.data.ContactStatus

/**
 * Database mein 'campaigns' table ko represent karta hai.
 * Ismein campaign se judi saari jaankari save hoti hai.
 */
@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey val id: String,
    val groupId: String, // Yeh group ID ya sheet campaign ke liye ek unique identifier ho sakta hai
    val campaignName: String,
    val message: String,
    val timestamp: Long,
    val totalContacts: Int,
    val contactStatuses: List<ContactStatus>,
    val isStopped: Boolean,
    val isRunning: Boolean,
    // Campaign ka type batata hai (jaise, "BULKSEND", "BULKTEXT", "SHEETSSEND")
    val campaignType: String,
    // Neeche diye gaye fields sirf "Sheet Campaign" ke liye hain
    val sheetFileName: String? = null,
    val countryCode: String? = null,
    val sheetDataJson: String? = null // Poori sheet ka data JSON format mein save karne ke liye
) {
    // Inhein database mein save nahi kiya jayega. Yeh `contactStatuses` se calculate honge.
    val sentCount: Int
        get() = contactStatuses.count { it.status == "sent" }

    val failedCount: Int
        get() = contactStatuses.count { it.status == "failed" }
}

