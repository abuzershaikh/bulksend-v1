package com.message.bulksend.plan

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.message.bulksend.auth.UserManager
import kotlinx.coroutines.tasks.await

class GetActivity : ComponentActivity(), PaymentResultListener {
    
    companion object {
        const val TAG = "GetActivity"
        const val CREATE_ORDER_URL = "https://us-central1-mailtracker-demo.cloudfunctions.net/createOrder"
        const val VERIFY_PAYMENT_URL = "https://us-central1-mailtracker-demo.cloudfunctions.net/verifyPayment"
        const val RAZORPAY_KEY_ID = "rzp_test_RSrYAbl5jpzM7x"
        
        // OkHttp Client
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    private var currentOrderId: String? = null
    private var selectedPlan: String = "lifetime"
    private val auth = FirebaseAuth.getInstance()
    private val userManager by lazy { UserManager(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Razorpay
        Checkout.preload(applicationContext)
        
        selectedPlan = intent.getStringExtra("SELECTED_PLAN") ?: "lifetime"
        
        setContent {
            MaterialTheme {
                GetActivityScreen(
                    activity = this,
                    selectedPlan = selectedPlan,
                    onPaymentComplete = {
                        Toast.makeText(this, "✅ Payment Successful!", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onPaymentFailed = { error ->
                        Toast.makeText(this, "❌ Payment Failed: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
    
    // Razorpay Payment Success
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        Log.d(TAG, "Payment Success: $razorpayPaymentId")
        Log.d(TAG, "Order ID: $currentOrderId")
        
        if (razorpayPaymentId != null && currentOrderId != null) {
            // Show success immediately
            runOnUiThread {
                Toast.makeText(
                    this@GetActivity,
                    "✅ Payment Successful!\nPayment ID: $razorpayPaymentId",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            // Update Firebase with premium plan
            lifecycleScope.launch {
                try {
                    val userEmail = auth.currentUser?.email
                    if (userEmail != null) {
                        // Update user plan in Firebase
                        val success = updateUserPlanInFirebase(userEmail, selectedPlan, razorpayPaymentId)
                        
                        if (success) {
                            Log.d(TAG, "Firebase updated successfully")
                            
                            // Fetch updated data from Firebase and save to SharedPreferences
                            val updatedUserData = userManager.getUserData(userEmail)
                            if (updatedUserData != null) {
                                saveSubscriptionPreferences(updatedUserData)
                                Log.d(TAG, "SharedPreferences updated successfully")
                            }
                            
                            runOnUiThread {
                                Toast.makeText(
                                    this@GetActivity,
                                    "✅ Premium activated successfully!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Log.e(TAG, "Failed to update Firebase")
                            runOnUiThread {
                                Toast.makeText(
                                    this@GetActivity,
                                    "⚠️ Payment successful but failed to activate premium. Contact support.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    
                    // Wait a moment to show the toast
                    kotlinx.coroutines.delay(2000)
                    
                    // Close activity with success result
                    runOnUiThread {
                        setResult(RESULT_OK)
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing payment", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@GetActivity,
                            "⚠️ Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "⚠️ Payment data incomplete", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Update user plan in Firebase
    private suspend fun updateUserPlanInFirebase(
        email: String,
        planType: String,
        paymentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentTime = Timestamp.now()
            
            // Calculate end date based on plan type (only monthly and lifetime)
            val daysToAdd = when (planType) {
                "monthly" -> 30L
                "lifetime" -> 36500L // 100 years for lifetime
                else -> 30L
            }
            
            val endTime = Timestamp(currentTime.seconds + (daysToAdd * 24 * 60 * 60), 0)
            
            // Update user subscription in Firebase
            val userData = userManager.getUserData(email)
            if (userData != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("email_data")
                    .document(email)
                    .update(
                        mapOf(
                            "subscriptionType" to "premium",
                            "planType" to planType, // monthly or lifetime
                            "subscriptionStartDate" to currentTime,
                            "subscriptionEndDate" to endTime,
                            "contactsLimit" to -1, // -1 means unlimited
                            "groupsLimit" to -1, // -1 means unlimited
                            "lastPaymentId" to paymentId,
                            "lastPaymentDate" to currentTime
                        )
                    )
                    .await()
                
                Log.d(TAG, "User $email upgraded to $planType plan")
                true
            } else {
                Log.e(TAG, "User data not found for $email")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Firebase", e)
            false
        }
    }
    
    // Save subscription preferences to SharedPreferences
    private fun saveSubscriptionPreferences(userData: com.message.bulksend.data.UserData) {
        try {
            val sharedPref = getSharedPreferences("subscription_prefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("subscription_type", userData.subscriptionType)
                putInt("contacts_limit", userData.contactsLimit)
                putInt("current_contacts", userData.currentContactsCount)
                putInt("groups_limit", userData.groupsLimit)
                putInt("current_groups", userData.currentGroupsCount)
                putString("user_email", userData.email)

                // Save expiry info for premium users
                if (userData.subscriptionType == "premium") {
                    userData.subscriptionEndDate?.let { endDate ->
                        putLong("subscription_end_time", endDate.seconds * 1000)
                    }
                } else {
                    remove("subscription_end_time")
                }

                apply()
            }

            Log.d(TAG, "✅ Subscription preferences saved:")
            Log.d(TAG, "  Type: ${userData.subscriptionType}")
            Log.d(TAG, "  Contacts: ${userData.currentContactsCount}/${userData.contactsLimit}")
            Log.d(TAG, "  Groups: ${userData.currentGroupsCount}/${userData.groupsLimit}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving subscription preferences", e)
        }
    }
    
    // Razorpay Payment Error
    override fun onPaymentError(code: Int, response: String?) {
        Log.e(TAG, "Payment Error: $code - $response")
        Toast.makeText(this, "❌ Payment Failed: $response", Toast.LENGTH_LONG).show()
    }
    
    // Launch Razorpay Checkout
    fun launchRazorpayCheckout(orderDetails: OrderResponse) {
        currentOrderId = orderDetails.orderId
        
        val checkout = Checkout()
        checkout.setKeyID(orderDetails.keyId)
        
        try {
            // Get user email from Firebase Auth
            val userEmail = auth.currentUser?.email
            
            val options = JSONObject()
            options.put("name", "Premium Subscription")
            options.put("description", orderDetails.planName)
            options.put("order_id", orderDetails.orderId)
            options.put("currency", orderDetails.currency)
            options.put("amount", orderDetails.amount)
            
            // Prefill user details
            val prefill = JSONObject()
            
            // Add email if available from auth
            if (!userEmail.isNullOrEmpty()) {
                prefill.put("email", userEmail)
                
                // Make email readonly if prefilled from auth
                options.put("readonly", JSONObject().apply {
                    put("email", true)
                })
            }
            
            // Add empty contact field for user to fill
            prefill.put("contact", "")
            options.put("prefill", prefill)
            
            // Theme
            val theme = JSONObject()
            theme.put("color", "#0D47A1")
            options.put("theme", theme)
            
            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Razorpay", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun GetActivityScreen(
    activity: GetActivity,
    selectedPlan: String,
    onPaymentComplete: () -> Unit,
    onPaymentFailed: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var orderDetails by remember { mutableStateOf<OrderResponse?>(null) }
    val scope = rememberCoroutineScope()
    
    // Create order on screen load
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                val response = createOrder(selectedPlan)
                orderDetails = response
                Log.d(GetActivity.TAG, "Order created: ${response.orderId}")
            } catch (e: Exception) {
                Log.e(GetActivity.TAG, "Failed to create order", e)
                onPaymentFailed("Failed to create order: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D47A1),
                        Color(0xFF1976D2),
                        Color(0xFF42A5F5)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Payment Gateway",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Selected Plan: ${if (selectedPlan == "monthly") "Monthly (₹99)" else "Lifetime (₹1,500)"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0D47A1),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF0D47A1)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Creating order...",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    } else if (orderDetails != null) {
                        Text(
                            text = "Order ID: ${orderDetails!!.orderId}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Amount: ₹${orderDetails!!.amount / 100}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D47A1)
                        )
                    } else {
                        Text(
                            text = "Ready to process payment",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            if (orderDetails != null) {
                                // Launch Razorpay checkout
                                activity.launchRazorpayCheckout(orderDetails!!)
                            }
                        },
                        enabled = !isLoading && orderDetails != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D47A1)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Proceed to Payment",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// Data classes
data class OrderResponse(
    val success: Boolean,
    val orderId: String,
    val amount: Int,
    val currency: String,
    val keyId: String,
    val planName: String
)

data class VerifyResponse(
    val success: Boolean,
    val verified: Boolean,
    val message: String
)

// API Functions
suspend fun createOrder(planType: String): OrderResponse = withContext(Dispatchers.IO) {
    try {
        // Request body
        val requestBody = JSONObject().apply {
            put("planType", planType)
        }
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toString().toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(GetActivity.CREATE_ORDER_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        Log.d(GetActivity.TAG, "Creating order for plan: $planType")
        
        GetActivity.httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            
            Log.d(GetActivity.TAG, "Response code: ${response.code}")
            Log.d(GetActivity.TAG, "Response body: $responseBody")
            
            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code} - $responseBody")
            }
            
            val json = JSONObject(responseBody)
            OrderResponse(
                success = json.getBoolean("success"),
                orderId = json.getString("orderId"),
                amount = json.getInt("amount"),
                currency = json.getString("currency"),
                keyId = json.getString("keyId"),
                planName = json.getString("planName")
            )
        }
    } catch (e: Exception) {
        Log.e(GetActivity.TAG, "Error creating order", e)
        throw e
    }
}

suspend fun verifyPayment(
    orderId: String,
    paymentId: String,
    signature: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        // Request body
        val requestBody = JSONObject().apply {
            put("razorpay_order_id", orderId)
            put("razorpay_payment_id", paymentId)
            put("razorpay_signature", signature)
        }
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toString().toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(GetActivity.VERIFY_PAYMENT_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        Log.d(GetActivity.TAG, "Verifying payment: $paymentId")
        
        GetActivity.httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            
            Log.d(GetActivity.TAG, "Verify response: $responseBody")
            
            if (!response.isSuccessful) {
                Log.e(GetActivity.TAG, "Verification failed: ${response.code}")
                return@withContext false
            }
            
            val json = JSONObject(responseBody)
            json.getBoolean("verified")
        }
    } catch (e: Exception) {
        Log.e(GetActivity.TAG, "Error verifying payment", e)
        false
    }
}
