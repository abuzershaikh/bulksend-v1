package com.message.bulksend

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.anylatic.ReportlistActivity
import com.message.bulksend.bulksend.BulksendActivity

import com.message.bulksend.bulksend.CampaignStatusActivity
import com.message.bulksend.bulksend.SelectActivity
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.support.SupportActivity
import com.message.bulksend.templates.TemplateActivity
import com.message.bulksend.auth.UserProfileActivity
import com.message.bulksend.tutorial.FaqActivity

import com.message.bulksend.ui.theme.BulksendTestTheme
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Firebase Analytics
        firebaseAnalytics = Firebase.analytics
        
        // Log app open event
        logAnalyticsEvent("app_opened", null)
        
        // Check if user has agreed to accessibility terms
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasAgreedToAccessibility = prefs.getBoolean("accessibility_agreed", false)
        
        setContent {
            BulksendTestTheme {
                var showAccessibilityDialog by remember { mutableStateOf(!hasAgreedToAccessibility) }
                
                MainScreen()
                
                // Show accessibility dialog if not agreed
                if (showAccessibilityDialog) {
                    AccessibilityDialog(
                        onAgree = {
                            // Save agreement to SharedPreferences
                            prefs.edit().putBoolean("accessibility_agreed", true).apply()
                            showAccessibilityDialog = false
                        },
                        onDisagree = {
                            // Keep showing dialog - don't close
                            Toast.makeText(
                                this@MainActivity,
                                "You must agree to continue using the app",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
    
    // Helper function to log analytics events
    private fun logAnalyticsEvent(eventName: String, params: Bundle?) {
        firebaseAnalytics.logEvent(eventName, params)
    }
    
    override fun onResume() {
        super.onResume()
        // Log screen view
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })
    }
}

// Data classes to hold UI information
data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val hasBadge: Boolean = false,
    val badgeText: String = "",
    val gradient: List<Color> = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)),
    val onClick: () -> Unit = {} // Added onClick lambda
)

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(50) // Adjust typing speed here
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        style = style
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val context = LocalContext.current

    TopAppBar(
        title = {
            Text(
                "ChatsPromo",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        actions = {
            // Premium Button with Animation
            AnimatedPremiumButton(
                onClick = {
                    context.startActivity(Intent(context, com.message.bulksend.plan.PrepackActivity::class.java))
                }
            )

            // Profile Button
            IconButton(
                onClick = {
                    context.startActivity(Intent(context, UserProfileActivity::class.java))
                }
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = "Profile",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF4F46E5),
            titleContentColor = Color.White
        )
    )
}

@Composable
fun AnimatedPremiumButton(onClick: () -> Unit) {
    // Infinite scale animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "premium_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "premium_scale"
    )
    
    IconButton(onClick = onClick) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_premium),
            contentDescription = "Get Premium",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current // Get context for intents
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8FAFC),
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9)
        )
    )

    Scaffold(
        topBar = { TopBar() },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(backgroundBrush),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { AnimatedSendMessageCard() }
                item { AnimatedReportButtons() }
                item {
                    AnimatedFeatureSection(
                        title = "🚀 Bulk Sending",
                        items = listOf(
                            // 1. Contacts List
                            FeatureItem(
                                Icons.Outlined.ContactMail,
                                "Contacts\nList",
                                gradient = listOf(Color(0xFFE8F5E8), Color(0xFFC8E6C9)),
                                onClick = {
                                    context.startActivity(Intent(context, ContactzActivity::class.java))
                                }
                            ),
                            // 2. Manage Templates
                            FeatureItem(
                                Icons.Outlined.Style,
                                "Manage Templates",
                                gradient = listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7)),
                                onClick = {
                                    context.startActivity(Intent(context, TemplateActivity::class.java))
                                }
                            ),
                            // 3. Grab Unsaved Chat Contact
                            FeatureItem(
                                Icons.Outlined.ContactPage,
                                "Grab Unsaved Chat Contact",
                                gradient = listOf(Color(0xFFFFF3E0), Color(0xFFFFCC02)),
                                onClick = {
                                    context.startActivity(Intent(context, com.message.bulksend.waextract.TextExtractActivity::class.java))
                                }
                            )
                        )
                    )
                }
                item {
                    AnimatedFeatureSection(
                        title = "✨ New Features",
                        items = listOf(
                            FeatureItem(
                                Icons.Outlined.TableChart,
                                "Chat Reports",
                                gradient = listOf(Color(0xFFE8EAF6), Color(0xFFC5CAE9)),
                                onClick = { Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show() }
                            ),
                            FeatureItem(
                                Icons.Outlined.PlaylistRemove,
                                "Unsubscriber List",
                                gradient = listOf(Color(0xFFF1F8E9), Color(0xFFDCEDC8)),
                                onClick = { Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show() }
                            )
                        )
                    )
                }
                item {
                    AnimatedFeatureSection(
                        title = "🔧 Others",
                        items = listOf(
                            FeatureItem(
                                Icons.Outlined.SupportAgent,
                                "Support",
                                gradient = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)),
                                onClick = {
                                    context.startActivity(Intent(context, SupportActivity::class.java))
                                }
                            ),
                            FeatureItem(
                                Icons.Outlined.Quiz,
                                "FAQs",
                                gradient = listOf(Color(0xFFFFF8E1), Color(0xFFFFE082)),
                                onClick = {
                                    context.startActivity(Intent(context, FaqActivity::class.java))
                                }
                            ),
                            FeatureItem(
                                Icons.Outlined.HelpOutline,
                                "How to use?",
                                gradient = listOf(Color(0xFFE8F5E8), Color(0xFFC8E6C9)),
                                onClick = {
                                    context.startActivity(Intent(context, FaqActivity::class.java))
                                }
                            )
                        )
                    )
                }
            }
        }
    )
}



@Composable
fun AnimatedSendMessageCard() {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = ""
    )

    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF667eea),
                            Color(0xFF764ba2),
                            Color(0xFF6B73FF)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TypewriterText(
                        "Send Message",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TypewriterText(
                        "Create campaign and send bulk messages",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "")
                val buttonScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                val context = LocalContext.current

                Button(
                    onClick = {
                        val intent = Intent(context, SelectActivity::class.java)
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.scale(buttonScale)
                ) {
                    Text(
                        "Start",
                        color = Color(0xFF667eea),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedReportButtons() {
    var isVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current // Get context for starting activity

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Define button data and actions
        val reportItems = listOf(
            Triple(Icons.Outlined.Message, "Message Reports") {
                context.startActivity(Intent(context, ReportlistActivity::class.java))
            },
            Triple(Icons.Outlined.Campaign, "Campaign Status") {
                // Action to start CampaignstatusActivity
                context.startActivity(Intent(context, CampaignStatusActivity::class.java))
            }
        )

        reportItems.forEachIndexed { index, (icon, text, onClickAction) ->
            val offsetX by animateIntAsState(
                targetValue = if (isVisible) 0 else if (index == 0) -300 else 300,
                animationSpec = tween(800, delayMillis = index * 100), label = ""
            )

            AnimatedReportButton(
                icon = icon,
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = offsetX.dp),
                onClick = onClickAction // Pass the defined action
            )
        }
    }
}

@Composable
fun AnimatedReportButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit // Accept an onClick lambda
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100), label = ""
    )

    Button(
        onClick = onClick, // Use the passed onClick lambda
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        modifier = modifier
            .height(70.dp)
            .scale(scale),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = Color(0xFF4F46E5),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}


@Composable
fun AnimatedFeatureSection(title: String, items: List<FeatureItem>) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(900)
        isVisible = true
    }

    val slideOffset by animateIntAsState(
        targetValue = if (isVisible) 0 else 100,
        animationSpec = tween(800), label = ""
    )

    Column(
        modifier = Modifier.offset(y = slideOffset.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items.chunked(3).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Top
                ) {
                    rowItems.forEachIndexed { itemIndex, item ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedFeatureGridItem(
                                item = item,
                                delay = (rowIndex * 3 + itemIndex) * 100
                            )
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedFeatureGridItem(item: FeatureItem, delay: Int = 0) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = ""
    )
    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 0f else -180f,
        animationSpec = tween(600, delayMillis = delay), label = ""
    )

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        isVisible = true
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .scale(scale)
            .clickable(onClick = item.onClick) // Updated to use onClick from item
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(item.gradient)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(rotation),
                    tint = Color(0xFF374151)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                color = Color(0xFF374151),
                fontWeight = FontWeight.Medium
            )
        }

        if (item.hasBadge) {
            val badgeScale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ), label = ""
            )

            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)
                    .scale(badgeScale),
                containerColor = Color(0xFFEF4444)
            ) {
                if (item.badgeText.isNotEmpty()) {
                    Text(
                        item.badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AccessibilityDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Don't allow dismiss by clicking outside */ },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Accessibility,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4F46E5)
            )
        },
        title = {
            Text(
                text = "Accessibility Permission",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This app uses Accessibility Service for automation purposes.",
                    fontSize = 15.sp,
                    color = Color(0xFF374151)
                )
                
                Text(
                    text = "Core Features:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F2937)
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BulletPoint("Automate message sending")
                    BulletPoint("Read and interact with WhatsApp")
                    BulletPoint("Improve user experience")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "⚠️ We respect your privacy and only use this permission for app functionality.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "I Agree",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDisagree,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "Disagree",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
fun BulletPoint(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            fontSize = 16.sp,
            color = Color(0xFF4F46E5),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF374151)
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun MainScreenPreview() {
    BulksendTestTheme {
        MainScreen()
    }
}
