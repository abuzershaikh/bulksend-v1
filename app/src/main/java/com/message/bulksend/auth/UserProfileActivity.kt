package com.message.bulksend.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.data.UserData
import com.message.bulksend.utils.DeviceUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UserProfileActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val userManager by lazy { UserManager(this) }
    private val emailService by lazy { EmailService(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UserProfileScreen(
                onLogout = { handleLogout() },
                onSendSupportEmail = { issue ->
                    auth.currentUser?.email?.let { email ->
                        emailService.sendSupportEmail(email, issue)
                    }
                }
            )
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            auth.currentUser?.email?.let { email ->
                userManager.logoutUser(email)
            }

            // Navigate back to auth screen
            startActivity(Intent(this@UserProfileActivity, AuthActivity::class.java))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onLogout: () -> Unit,
    onSendSupportEmail: (String) -> Unit
) {
    var userData by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val userManager = UserManager(androidx.compose.ui.platform.LocalContext.current)

    // Load user data
    LaunchedEffect(Unit) {
        auth.currentUser?.email?.let { email ->
            userData = userManager.getUserData(email)
        }
        isLoading = false
    }

    val gradientColors = listOf(
        Color(0xFF1534DE),
        Color(0xFF4611AB),
        Color(0xFFB90FD3)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(gradientColors)
            )
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    "Profile",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            actions = {
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color.White
                    )
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            userData?.let { user ->
                ProfileContent(
                    userData = user,
                    onSendSupportEmail = onSendSupportEmail
                )
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileContent(
    userData: UserData,
    onSendSupportEmail: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = userData.displayName.ifEmpty { "User" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = userData.email,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ID: ${userData.uniqueIdentifier}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Subscription Information
        var subscriptionInfo by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        val userManager = UserManager(context)

        LaunchedEffect(userData.email) {
            subscriptionInfo = userManager.getSubscriptionInfo(userData.email)
        }

        ProfileSection(
            title = "💎 Subscription Plan",
            items = buildList {
                val type = subscriptionInfo["type"] as? String ?: "free"
                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                val planType = subscriptionInfo["planType"] as? String ?: ""

                // Show plan type with monthly/lifetime info
                val planDisplay = when {
                    type == "premium" && planType == "monthly" -> "💎 Premium (Monthly)"
                    type == "premium" && planType == "lifetime" -> "💎 Premium (Lifetime)"
                    type == "premium" -> "💎 Premium"
                    else -> "Free"
                }

                add(ProfileItem(
                    Icons.Default.Star,
                    "Plan Type",
                    planDisplay
                ))

                if (type == "premium") {
                    subscriptionInfo["endDate"]?.let { endDate ->
                        val dateStr = dateFormat.format((endDate as com.google.firebase.Timestamp).toDate())
                        add(ProfileItem(
                            Icons.Default.Schedule,
                            "Plan Expires",
                            if (isExpired) "❌ Expired" else "✅ $dateStr"
                        ))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Account Information
        ProfileSection(
            title = "Account Information",
            items = listOf(
                ProfileItem(Icons.Default.DateRange, "First Signup", dateFormat.format(userData.firstSignupDate.toDate())),
                ProfileItem(Icons.Default.Schedule, "Last Login", dateFormat.format(userData.lastLoginDate.toDate())),
                ProfileItem(Icons.Default.CheckCircle, "Status", if (userData.isActive) "Active" else "Inactive")
            )
        )



        Spacer(modifier = Modifier.height(24.dp))

        // Settings Button
        Button(
            onClick = {
                context.startActivity(Intent(context, UserPreferencesActivity::class.java))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Settings",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Support Button
        Button(
            onClick = { onSendSupportEmail("I need help with my account") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                Icons.Default.Support,
                contentDescription = "Support",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Contact Support",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ProfileSection(
    title: String,
    items: List<ProfileItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            items.forEach { item ->
                ProfileItemRow(item)
                if (item != items.last()) {
                    Divider(
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileItemRow(item: ProfileItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = item.value,
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class ProfileItem(
    val icon: ImageVector,
    val label: String,
    val value: String
)