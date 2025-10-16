package com.message.bulksend.plan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.auth.UserManager
import com.message.bulksend.R
import kotlinx.coroutines.launch

class PrepackActivity : ComponentActivity() {
    private lateinit var billingManager: BillingManager
    private val auth = FirebaseAuth.getInstance()
    private val userManager by lazy { UserManager(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize billing manager
        billingManager = BillingManager(
            context = this,
            onPurchaseSuccess = { purchase ->
                // Handle successful purchase - Firebase already updated in BillingManager
                runOnUiThread {
                    showSuccessMessage()
                    // Close activity after successful purchase
                    android.os.Handler(mainLooper).postDelayed({
                        finish()
                    }, 2000)
                }
            },
            onPurchaseFailure = { error ->
                // Handle purchase failure
                runOnUiThread {
                    showErrorMessage(error)
                }
            }
        )
        billingManager.initialize()
        
        setContent {
            MaterialTheme {
                var productPrices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                
                // Listen for product prices from Play Store
                LaunchedEffect(Unit) {
                    billingManager.setOnProductsLoadedListener { prices ->
                        productPrices = prices
                    }
                }
                
                GetPremiumScreen(
                    activity = this,
                    billingManager = billingManager,
                    productPrices = productPrices,
                    onContinue = { selectedPlan ->
                        openGetActivity(selectedPlan)
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    private val getActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // When user comes back from GetActivity, refresh subscription data
        if (result.resultCode == RESULT_OK) {
            refreshSubscriptionData { success ->
                if (success) {
                    // Close PrepackActivity after successful refresh
                    android.os.Handler(mainLooper).postDelayed({
                        finish()
                    }, 1000)
                }
            }
        }
    }
    
    private fun openGetActivity(plan: String) {
        val intent = Intent(this, GetActivity::class.java).apply {
            putExtra("SELECTED_PLAN", plan)
        }
        getActivityLauncher.launch(intent)
    }
    
    private fun showSuccessMessage() {
        android.widget.Toast.makeText(
            this,
            "✅ Purchase successful! Premium activated.\nYour plan has been updated in your account.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    private fun showErrorMessage(error: String) {
        android.widget.Toast.makeText(
            this,
            "❌ $error",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    // Refresh subscription data from Firebase
    fun refreshSubscriptionData(onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                val userEmail = auth.currentUser?.email
                if (userEmail != null) {
                    // Fetch latest data from Firebase
                    val userData = userManager.getUserData(userEmail)
                    
                    if (userData != null) {
                        // Save to SharedPreferences
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
                        
                        runOnUiThread {
                            android.widget.Toast.makeText(
                                this@PrepackActivity,
                                "✅ Subscription data refreshed!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onComplete(true)
                        }
                    } else {
                        runOnUiThread {
                            android.widget.Toast.makeText(
                                this@PrepackActivity,
                                "❌ Failed to fetch data",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onComplete(false)
                        }
                    }
                } else {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PrepackActivity,
                            "❌ User not logged in",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PrepackActivity,
                        "❌ Error: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onComplete(false)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }
}

data class Feature(
    val name: String,
    val free: String,
    val pro: String,
    val isNew: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetPremiumScreen(
    activity: ComponentActivity,
    billingManager: BillingManager,
    productPrices: Map<String, String>,
    onContinue: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("lifetime") }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // Get prices from Play Store or use defaults
    val monthlyPrice = productPrices[BillingManager.PRODUCT_MONTHLY] ?: "₹149"
    val lifetimePrice = productPrices[BillingManager.PRODUCT_LIFETIME] ?: "₹999"

    val features = listOf(
        Feature("Remove Ads", "✗", "✓"),
        Feature("Message Unknown Contact", "✓", "✓"),
        Feature("Number of Campaigns", "01", "∞"),
        Feature("Export Campaign", "✓", "✓"),
        Feature("Maximum Contacts", "10", "∞"),
        Feature("Import Sheet, CSV, WP Group", "✓", "✓"),
        Feature("Maximum Groups", "5", "∞"),
        Feature("Unique Identity", "✓", "✓"),
        Feature("Random Delay", "✓", "✓"),
        Feature("Export Report", "✓", "✓")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Unlock Premium",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A2E)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF666666)
                        )
                    }
                },
                actions = {
                    // Refresh Button
                    IconButton(
                        onClick = {
                            if (!isRefreshing && activity is PrepackActivity) {
                                isRefreshing = true
                                activity.refreshSubscriptionData { success ->
                                    isRefreshing = false
                                }
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF00D4FF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Subscription",
                                tint = Color(0xFF00D4FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.shadow(elevation = 4.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8F9FF),
                            Color(0xFFF0E6FF)
                        )
                    )
                )
                .verticalScroll(scrollState)
        ) {


            // Features Comparison Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00D4FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Compare Plans",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1A1A2E)
                            )
                        }

                        // Free Badge
                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8E8F0))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "FREE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF666666)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Pro Badge with Gradient
                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF6B6B),
                                            Color(0xFFFF8E53)
                                        )
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE8E8F0), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable Feature Rows
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val featureScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(featureScrollState)
                        ) {
                            features.forEach { feature ->
                                FeatureRow(feature)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            // Pricing Section Title
            Text(
                text = "Select Your Perfect Plan",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1A2E),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            // Pricing Cards
            val horizontalScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Monthly Plan
                PricingCard(
                    title = "Monthly",
                    price = monthlyPrice,
                    period = "per month",
                    savings = null,
                    isSelected = selectedPlan == "monthly",
                    isPopular = false,
                    onClick = { selectedPlan = "monthly" },
                    modifier = Modifier.width(140.dp),
                    colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                )

                // Lifetime Plan - MOST POPULAR
                PricingCard(
                    title = "Lifetime",
                    price = lifetimePrice,
                    period = "one-time",
                    savings = "Best Value",
                    isSelected = selectedPlan == "lifetime",
                    isPopular = true,
                    onClick = { selectedPlan = "lifetime" },
                    modifier = Modifier.width(140.dp),
                    colors = listOf(Color(0xFF333333), Color(0xFF333333))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Button
            ShimmerContinueButton(
                onClick = { showPaymentDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    
    // Payment Dialog
    if (showPaymentDialog) {
        PaymentDialog(
            planName = if (selectedPlan == "monthly") "Monthly Plan" else "Lifetime Plan",
            planPrice = if (selectedPlan == "monthly") monthlyPrice else lifetimePrice,
            onDismiss = { showPaymentDialog = false },
            onPlayStoreClick = {
                showPaymentDialog = false
                // Launch Play Store billing
                val productId = if (selectedPlan == "monthly") {
                    BillingManager.PRODUCT_MONTHLY
                } else {
                    BillingManager.PRODUCT_LIFETIME
                }
                billingManager.launchPurchaseFlow(activity, productId)
            },
            onRazorpayClick = {
                showPaymentDialog = false
                // Launch Razorpay payment
                onContinue(selectedPlan)
            }
        )
    }
}

@Composable
fun FeatureRow(feature: Feature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature.name,
                fontSize = 13.sp,
                color = Color(0xFF333333),
                fontWeight = FontWeight.Medium
            )
            if (feature.isNew) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF6B6B),
                                    Color(0xFFFF8E53)
                                )
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "✨ NEW",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        // Free Value
        Box(
            modifier = Modifier.width(65.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                feature.free == "✓" -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(20.dp)
                )
                feature.free == "✗" -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
                else -> Text(
                    text = feature.free,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pro Value
        Box(
            modifier = Modifier.width(65.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                feature.pro == "✓" -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(20.dp)
                )
                feature.pro == "✗" -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
                else -> Text(
                    text = feature.pro,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
    }
}

@Composable
fun PricingCard(
    title: String,
    price: String,
    period: String,
    savings: String?,
    isSelected: Boolean,
    isPopular: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .scale(if (isPopular || isSelected) 1.08f else 1f)
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = if (isPopular) Color(0xFF4CAF50) else colors[0],
                        shape = RoundedCornerShape(16.dp)
                    )
                } else if (!isPopular) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFFE8E8F0),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            )
            .shadow(
                elevation = if (isPopular || isSelected) 24.dp else 4.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = if (isPopular) 0.dp else 14.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val priceScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(priceScrollState),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = price,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors[0],
                        maxLines = 1
                    )
                }

                Text(
                    text = period,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }

            if (isPopular && savings != null) {
                PremiumBadge(savings, colors)
            } else if (savings != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF5E6))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = savings,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF8E53)
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumBadge(text: String, colors: List<Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "badge_shimmer"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(Color(0xFFFF9800))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerTranslate, 0f),
                        end = Offset(shimmerTranslate + 100f, 0f)
                    )
                )
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
fun BenefitPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
fun ShimmerContinueButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "button_shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "button_shimmer"
    )

    var isPressed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(56.dp)
                .scale(if (isPressed) 0.95f else 1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF6B6B),
                            Color(0xFFFF8E53)
                        )
                    )
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerTranslate, 0f),
                            end = Offset(shimmerTranslate + 150f, 0f)
                        )
                    )
                }
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Continue to Premium",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}


@Composable
fun PaymentDialog(
    planName: String,
    planPrice: String,
    onDismiss: () -> Unit,
    onPlayStoreClick: () -> Unit,
    onRazorpayClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }
                
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Payment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Choose Payment Method",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF333333)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$planName - $planPrice",
                    fontSize = 16.sp,
                    color = Color(0xFF667EEA),
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Play Store Option
                PaymentOptionCardWithDrawable(
                    iconRes = R.drawable.ic_playstore,
                    title = "Pay with Play Store",
                    subtitle = "",
                    color = Color(0xFF34A853),
                    onClick = onPlayStoreClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Razorpay Option with Header
                PaymentOptionCardWithDrawable(
                    iconRes = R.drawable.ic_razorpay,
                    title = "Pay with Razorpay",
                    subtitle = "Currently Supported: India",
                    color = Color(0xFF0D47A1),
                    onClick = onRazorpayClick
                )
            }
        }
    }
}

@Composable
fun PaymentOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = color
            )
        }
    }
}

@Composable
fun PaymentOptionCardWithDrawable(
    iconRes: Int,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = color
            )
        }
    }
}
