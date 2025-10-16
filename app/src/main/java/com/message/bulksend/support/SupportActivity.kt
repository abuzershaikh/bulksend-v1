package com.message.bulksend.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

import kotlinx.coroutines.delay

class SupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                SupportScreen()
            }
        }
    }
}

// Enhanced main screen with animations
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen() {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Scaffold(
        topBar = {
            SupportTopAppBar(
                onBackClicked = { (context as? ComponentActivity)?.finish() }
            )
        },
        // Changed background to a simpler gradient
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) { padding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(1000, easing = EaseOutBounce)
            ) + fadeIn(animationSpec = tween(1000))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero section
                item {
                    WelcomeCard()
                }

                // Animated contact cards
                itemsIndexed(contactItems) { index, item ->
                    AnimatedContactCard(
                        item = item,
                        index = index,
                        onClick = { handleContactClick(context, item.action, item.subtitle) }
                    )
                }

                // Section divider
                item {
                    AnimatedSectionDivider()
                }

                // Pages section header
                item {
                    AnimatedSectionHeader("Explore More")
                }

                // Page cards without animation delay
                items(pageItems) { item ->
                    PageCard(
                        item = item,
                        onClick = {
                            // Open WebViewActivity on click with URL
                            val intent = Intent(context, WebViewActivity::class.java).apply {
                                putExtra("TITLE", item.title)
                                putExtra("URL", item.url)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// Welcome Card (Static)
@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "We're Here to Help!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Get instant support and assistance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Top App Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTopAppBar(onBackClicked: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Help & Support",
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClicked) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// Animated Contact Card
@Composable
private fun AnimatedContactCard(
    item: ContactInfo,
    index: Int,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )

    val animationDelay = (index * 150).toLong()
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(animationDelay)
        startAnimation = true
    }

    AnimatedVisibility(
        visible = startAnimation,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(700, easing = EaseOutBounce)
        ) + fadeIn(animationSpec = tween(700))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable {
                    isPressed = true
                    onClick()
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent // Background is now from Box
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = getGradientColors(item.action)
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Color.White.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Page Card (No animation delay)
@Composable
private fun PageCard(
    item: PageInfo,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        getPageIconBackground(pageItems.indexOf(item)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Animated Section Header
@Composable
fun AnimatedSectionHeader(title: String) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(600, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(600))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

// Animated Section Divider
@Composable
fun AnimatedSectionDivider() {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(600, easing = EaseOutBounce)) +
                fadeIn(animationSpec = tween(600))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// Helper functions for colors
private fun getGradientColors(action: ContactAction): List<Color> {
    return when (action) {
        ContactAction.CALL -> listOf(Color(0xFF667eea), Color(0xFF764ba2))
        ContactAction.WHATSAPP -> listOf(Color(0xFF25D366), Color(0xFF128C7E))
        ContactAction.EMAIL -> listOf(Color(0xFFf093fb), Color(0xFFf5576c))
    }
}

private fun getPageIconBackground(index: Int): Brush {
    val colors = listOf(
        listOf(Color(0xFF667eea), Color(0xFF764ba2)),
        listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
        listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),
        listOf(Color(0xFF43e97b), Color(0xFF38f9d7)),
        listOf(Color(0xFFfa709a), Color(0xFFfee140))
    )
    return Brush.linearGradient(colors[index % colors.size])
}

// Data classes and enums remain the same
private enum class ContactAction { CALL, WHATSAPP, EMAIL }
private data class ContactInfo(val icon: ImageVector, val title: String, val subtitle: String, val action: ContactAction)
private data class PageInfo(val icon: ImageVector, val title: String, val url: String)

private val contactItems = listOf(
    ContactInfo(Icons.Default.Phone, "24x7 Customer Service", "+91 7400 212 304", ContactAction.CALL),
    ContactInfo(Icons.Default.Whatsapp, "WhatsApp Support", "+91 7400 212 304", ContactAction.WHATSAPP),
    ContactInfo(Icons.Default.Email, "Email Support", "autosavecontactsapp@gmail.com", ContactAction.EMAIL)
)

private val pageItems = listOf(
    PageInfo(Icons.Default.Info, "About Us", "https://chatspromo.blogspot.com/p/about-chatspromo-bulk-sender.html"),
    PageInfo(Icons.AutoMirrored.Filled.Article, "Terms & Conditions", "https://chatspromo.blogspot.com/p/terms-conditions.html"),
    PageInfo(Icons.Default.PrivacyTip, "Privacy Policy", "https://chatspromo.blogspot.com/p/privacy-policy-chatspromo-bulk-sender.html"),
    PageInfo(Icons.Default.CurrencyRupee, "Refund & Cancellation", "https://chatspromo.blogspot.com/p/cancellation-policy.html"),
    PageInfo(Icons.Default.Groups, "Join Our Community", "https://chatspromo.blogspot.com")
)

// Click handler remains the same
private fun handleContactClick(context: Context, action: ContactAction, data: String) {
    try {
        val intent = when (action) {
            ContactAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$data"))
            ContactAction.WHATSAPP -> {
                val formattedNumber = data.replace(" ", "").replace("+", "")
                Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber"))
            }
            ContactAction.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$data"))
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not perform action", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
fun SupportScreenPreview() {
    BulksendTestTheme {
        SupportScreen()
    }
}


