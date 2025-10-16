package com.message.bulksend.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.message.bulksend.MainActivity
import com.message.bulksend.R
import com.message.bulksend.data.UserData
import com.message.bulksend.utils.DeviceUtils
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {

    private val mAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val userManager by lazy { UserManager(this) }
    private val emailService by lazy { EmailService(this) }
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            SystemBarsColor(color = Color(0xFF2E3440))

            SignInScreen(
                onGoogleSignInClick = {
                    if (DeviceUtils.isNetworkAvailable(this)) {
                        val signInIntent = mGoogleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    } else {
                        showToast("🌐 No internet connection. Please check your network and try again.")
                    }
                },
                onWhatsAppSignInClick = {
                    openWhatsAppSignIn()
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            navigateToMain(currentUser)
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: Exception) {
                Log.w(TAG, "Google sign in failed", e)
                showToast("Google Sign-In failed.")
            }
        } else {
            showToast("Google Sign-In was cancelled.")
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    if (user != null) {
                        handleUserLogin(user)
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showToast("Firebase Authentication Failed.")
                }
            }
    }

    private fun handleUserLogin(user: FirebaseUser) {
        lifecycleScope.launch {
            try {
                if (!DeviceUtils.isNetworkAvailable(this@AuthActivity)) {
                    showToast("🌐 No internet connection. Please check your network and try again.")
                    mAuth.signOut()
                    return@launch
                }

                showToast("Setting up your account...")

                val deviceId = DeviceUtils.getDeviceId(this@AuthActivity)
                Log.d(TAG, "Login attempt - Email: ${user.email}, Device: $deviceId")

                // Check if user exists before creating/updating
                val existingUser = userManager.getUserData(user.email!!)
                val isNewUser = existingUser == null
                val oldDeviceId = existingUser?.deviceId

                val result = userManager.createOrUpdateUser(user)

                result.onSuccess { userData ->
                    Log.d(TAG, "User data created/updated successfully")

                    // Save subscription preferences locally
                    saveSubscriptionPreferences(userData)

                    if (isNewUser) {
                        // New user - send welcome email
                        emailService.sendWelcomeEmail(userData)
                        showToast("Welcome! Check your email for account details.")
                    } else if (oldDeviceId != null && oldDeviceId != deviceId) {
                        // Existing user with device change - send notification
                        emailService.sendDeviceChangeNotification(userData)
                        showToast("Welcome back! New device detected.")
                    } else {
                        // Same device login
                        showToast("Welcome back!")
                    }

                    navigateToMain(user)
                }.onFailure { exception ->
                    Log.e(TAG, "Error handling user login", exception)
                    showToast("❌ Error setting up account: ${exception.message}")
                    mAuth.signOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during login", e)
                showToast("An unexpected error occurred. Please try again.")
                mAuth.signOut()
            }
        }
    }

    private fun navigateToMain(user: FirebaseUser) {
        showToast("Welcome, ${user.displayName}")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openWhatsAppSignIn() {
        try {
            val phoneNumber = "+918779026964"
            val message = "Hi! I want to sign in to ChatsPromo app. Please help me with the authentication process. 🚀"

            val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
                setPackage("com.whatsapp")
            }

            if (whatsappIntent.resolveActivity(packageManager) != null) {
                startActivity(whatsappIntent)
            } else {
                val whatsappBusinessIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
                    setPackage("com.whatsapp.w4b")
                }

                if (whatsappBusinessIntent.resolveActivity(packageManager) != null) {
                    startActivity(whatsappBusinessIntent)
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
                    }
                    startActivity(browserIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp", e)
            showToast("❌ Unable to open WhatsApp. Please install WhatsApp or try Google Sign-In.")
        }
    }

    private fun saveSubscriptionPreferences(userData: UserData) {
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
            Log.d(TAG, "  Email: ${userData.email}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving subscription preferences", e)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "AuthActivity"
    }
}

@Composable
fun SystemBarsColor(color: Color) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window

    if (window != null) {
        SideEffect {
            window.statusBarColor = color.toArgb()
            window.navigationBarColor = color.toArgb()

            WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
                val isLight = color.luminance() > 0.5f
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }
}

@Composable
fun SignInScreen(
    onGoogleSignInClick: () -> Unit,
    onWhatsAppSignInClick: () -> Unit
) {
    var isGoogleLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Simple solid color background
    val backgroundColor = Color(0xFF2E3440)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Simple App Title
            Text(
                text = "ChatsPromo",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Version Badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "v1.0.0",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to ChatsPromo",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(50.dp))

            // Network Status Indicator
            NetworkStatusIndicator()

            Spacer(modifier = Modifier.height(30.dp))

            // Google Sign-In Button
            GoogleSignInButton(
                onClick = {
                    isGoogleLoading = true
                    onGoogleSignInClick()
                },
                isLoading = isGoogleLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Divider with "OR"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Text(
                    text = "  OR  ",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // WhatsApp Sign-In Button
            WhatsAppSignInButton(
                onClick = onWhatsAppSignInClick
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Security Notice
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔒 Secure Authentication",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "One email per device • End-to-end encrypted",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your messaging solution starts here",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NetworkStatusIndicator() {
    val context = LocalContext.current
    val isNetworkAvailable = DeviceUtils.isNetworkAvailable(context)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = "Network Status",
            tint = if (isNetworkAvailable) Color.Green else Color.Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isNetworkAvailable) "Connected" else "No Internet Connection",
            color = if (isNetworkAvailable) Color.Green else Color.Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4285F4) // Google Blue
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Signing you in...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GoogleIcon(
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Continue with Google",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WhatsAppSignInButton(
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val whatsappGreen = Color(0xFF25D366)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = whatsappGreen
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "WhatsApp sign-in",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Continue with WhatsApp",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GoogleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    // Simple "G" text as Google icon
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            color = tint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}