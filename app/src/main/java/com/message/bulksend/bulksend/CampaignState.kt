package com.message.bulksend.bulksend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ek aam (global) object jo campaign ki maujooda sthiti (state) ko rakhta hai.
 * Iska istemal 'BulksendActivity' aur 'WhatsAppAutoSendService' ke beech mein
 * jaankari share karne ke liye hota hai.
 */
object CampaignState {

    /**
     * Yeh batata hai ki kya Accessibility Service ne send button ko safaltapoorvak click kiya hai.
     * null: Abhi tak koi koshish nahi hui.
     * true: Safaltapoorvak click hua.
     * false: Click karne mein asafal raha.
     */
    var isSendActionSuccessful by mutableStateOf<Boolean?>(null)

    /**
     * Auto-send service ko enable/disable karne ke liye flag.
     * true: Service active hai aur WhatsApp messages bhej sakti hai.
     * false: Service inactive hai aur messages nahi bhejegi.
     */
    var isAutoSendEnabled by mutableStateOf(false)
}
