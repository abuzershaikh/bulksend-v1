package com.message.bulksend.waextract

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class PhoneNumberEntry(val number: String)

private const val PREFS_NAME = "PhoneNumbersPrefs"
private const val PREFS_KEY = "ExtractedNumbers"

class TextExtractActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                PhoneExtractorScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneExtractorScreen() {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var phoneNumbers by remember { mutableStateOf(loadPhoneNumbers(context)) }
    var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var seriesName by remember { mutableStateOf("Unknown") }
    var startFrom by remember { mutableStateOf("1") }
    var contactList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WhatsAppAutoSendService.ACTION_TEXT_CAPTURED) {
                val capturedText = intent.getStringExtra(WhatsAppAutoSendService.EXTRA_CAPTURED_TEXT)
                if (!capturedText.isNullOrEmpty()) {
                    rawText = capturedText
                    val newNumbers = extractPhoneNumbers(capturedText)
                    if (newNumbers.isNotEmpty()) {
                        val updatedNumbers = (phoneNumbers + newNumbers).distinctBy { it.number }
                        if (updatedNumbers.size > phoneNumbers.size) {
                            phoneNumbers = updatedNumbers
                            savePhoneNumbers(context!!, phoneNumbers)
                        }
                    }
                }
            }
        }
    }

    // Update contact list when phone numbers or series name changes
    LaunchedEffect(phoneNumbers, seriesName, startFrom) {
        val startNum = startFrom.toIntOrNull() ?: 1
        contactList = phoneNumbers.mapIndexed { index, entry ->
            val name = "$seriesName ${startNum + index}"
            "$name,${entry.number}"
        }
        
        if (phoneNumbers.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(phoneNumbers.size - 1)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        val filter = IntentFilter(WhatsAppAutoSendService.ACTION_TEXT_CAPTURED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, textReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, textReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        onDispose {
            context.unregisterReceiver(textReceiver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // Animated background
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val backgroundOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "backgroundOffset"
    )
    
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF667eea).copy(alpha = 0.1f + backgroundOffset * 0.1f),
                        Color(0xFF764ba2).copy(alpha = 0.1f + backgroundOffset * 0.1f),
                        Color(0xFFf093fb).copy(alpha = 0.1f + backgroundOffset * 0.1f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Analytics Card at top
                ExtractorAnalyticsCard(
                    contactCount = contactList.size,
                    isServiceEnabled = isServiceEnabled,
                    onEnableService = { showPrivacyDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input Section for Series Name
                ExtractorInputSection(
                    seriesName = seriesName,
                    onSeriesNameChange = { seriesName = it },
                    startFrom = startFrom,
                    onStartFromChange = { startFrom = it },
                    focusManager = focusManager
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Contact List
                ExtractorContactList(
                    contactList = contactList,
                    modifier = Modifier.heightIn(min = 300.dp, max = 600.dp)
                )
            }
            
            // Fixed bottom section with rounded card
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                        )
                    }
                    
                    // Action Buttons
                    ExtractorActionButtons(
                    onCopyAll = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val textToCopy = phoneNumbers.joinToString("\n") { it.number }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Numbers", textToCopy))
                        Toast.makeText(context, "${phoneNumbers.size} Numbers Copied!", Toast.LENGTH_SHORT).show()
                    },
                    onClear = {
                        phoneNumbers = emptyList()
                        rawText = ""
                        contactList = emptyList()
                        clearSavedPhoneNumbers(context)
                        WhatsAppAutoSendService.clearExtractedData()
                        Toast.makeText(context, "History Cleared!", Toast.LENGTH_SHORT).show()
                    },
                    onSaveVcf = {
                        if (contactList.isNotEmpty()) {
                            saveToVcf(contactList, context)
                        } else {
                            Toast.makeText(context, "No contacts to save", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveCsv = {
                        if (contactList.isNotEmpty()) {
                            saveToCsv(contactList, context)
                        } else {
                            Toast.makeText(context, "No contacts to save", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                }
            }
        }
    }
    
    // Privacy Dialog
    if (showPrivacyDialog) {
        PrivacyDialog(
            onAgree = {
                showPrivacyDialog = false
                // Enable contact extraction in the service
                WhatsAppAutoSendService.enableContactExtraction()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDisagree = {
                showPrivacyDialog = false
                Toast.makeText(context, "Accessibility permission required to extract contacts", Toast.LENGTH_LONG).show()
            }
        )
    }
    
    // Enable/disable contact extraction based on lifecycle
    DisposableEffect(Unit) {
        WhatsAppAutoSendService.enableContactExtraction()
        onDispose {
            WhatsAppAutoSendService.disableContactExtraction()
        }
    }
}

// Privacy Dialog
@Composable
fun PrivacyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDisagree) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                androidx.compose.material3.Text(
                    text = "Privacy Notice",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.material3.Text(
                    text = "To use this feature, we need Accessibility permission to extract contact numbers from WhatsApp.",
                    fontSize = 16.sp,
                    color = Color(0xFF4A5568),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                androidx.compose.material3.Text(
                    text = "• We only read WhatsApp contact numbers\n• No data is sent to any server\n• All data stays on your device\n• You can disable anytime",
                    fontSize = 14.sp,
                    color = Color(0xFF718096),
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDisagree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF667eea)
                        )
                    ) {
                        androidx.compose.material3.Text("Disagree", fontWeight = FontWeight.Bold)
                    }
                    
                    androidx.compose.material3.Button(
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea)
                        )
                    ) {
                        androidx.compose.material3.Text("Agree", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Helper Composables
@Composable
fun ExtractorHeader() {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF667eea), Color(0xFF764ba2))))
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.material3.Text(
                    text = "WhatsApp Contact Extractor",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ExtractorAnalyticsCard(
    contactCount: Int,
    isServiceEnabled: Boolean,
    onEnableService: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF4facfe), Color(0xFF00f2fe))))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            text = contactCount.toString(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.Text(
                            text = "Contacts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            if (isServiceEnabled) androidx.compose.material.icons.Icons.Default.CheckCircle 
                            else androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            text = if (isServiceEnabled) "Active" else "Inactive",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.Text(
                            text = "Service",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            text = "Real-time",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.Text(
                            text = "Extraction",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                if (!isServiceEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = onEnableService,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF667eea)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        androidx.compose.material3.Text("Enable Accessibility Service", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractorInputSection(
    seriesName: String,
    onSeriesNameChange: (String) -> Unit,
    startFrom: String,
    onStartFromChange: (String) -> Unit,
    focusManager: FocusManager
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            androidx.compose.material3.Text(
                text = "Contact Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = seriesName,
                    onValueChange = onSeriesNameChange,
                    label = { androidx.compose.material3.Text("Series Name") },
                    leadingIcon = { 
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Label, 
                            contentDescription = null
                        ) 
                    },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
                androidx.compose.material3.OutlinedTextField(
                    value = startFrom,
                    onValueChange = onStartFromChange,
                    label = { androidx.compose.material3.Text("Start") },
                    leadingIcon = { 
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Numbers, 
                            contentDescription = null
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
            }
        }
    }
}

@Composable
fun ExtractorContactList(
    contactList: List<String>,
    modifier: Modifier = Modifier
) {
    val cardGradients = listOf(
        listOf(Color(0xFFa8edea), Color(0xFFfed6e3)),
        listOf(Color(0xFFffecd2), Color(0xFFfcb69f)),
        listOf(Color(0xFFe0c3fc), Color(0xFF9bb5ff)),
        listOf(Color(0xFFfad0c4), Color(0xFFffd1ff)),
        listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb))
    )
    
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Contacts,
                    contentDescription = null,
                    tint = Color(0xFF667eea),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    text = "Extracted Contacts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (contactList.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        shape = CircleShape,
                        color = Color(0xFF667eea),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Text(
                                text = contactList.size.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            if (contactList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7FAFC), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E0),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Text(
                            text = "No contacts extracted yet",
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center
                        )
                        androidx.compose.material3.Text(
                            text = "Open WhatsApp and scroll through contacts",
                            color = Color(0xFFA0AEC0),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7FAFC), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    itemsIndexed(contactList) { index, contactString ->
                        ExtractorContactItem(
                            contactString = contactString,
                            index = index,
                            gradientColors = cardGradients[index % cardGradients.size]
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractorContactItem(
    contactString: String,
    index: Int,
    gradientColors: List<Color>
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(contactString) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = EaseOutQuart)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        val parts = contactString.split(",", limit = 2)
        val name = parts.getOrNull(0)?.trim() ?: "Unknown"
        val number = parts.getOrNull(1)?.trim() ?: ""

        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    androidx.compose.material3.Text(
                        text = number,
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = Color(0xFFE8E8F0),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF667eea),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractorActionButtons(
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
    onSaveVcf: () -> Unit,
    onSaveCsv: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCopyAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy All")
            }
            Button(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveCsv,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.TableChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV")
            }
            Button(
                onClick = onSaveVcf,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.ContactPage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("VCF")
            }
        }
    }
}

@Composable
fun ExtractorButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradientColors))
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                androidx.compose.material3.Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// Extract only phone numbers from text (ONLY + numbers)
private fun extractPhoneNumbers(text: String): List<PhoneNumberEntry> {
    val phoneNumbers = mutableListOf<PhoneNumberEntry>()
    val lines = text.split("\n")
    
    // ONLY match numbers starting with +
    val pattern = Regex("""\+\d{1,4}[\s-]?\(?\d{1,4}\)?[\s-]?\d{1,4}[\s-]?\d{1,9}""")
    
    lines.forEach { line ->
        val cleaned = line.trim()
        if (cleaned.isNotEmpty() && cleaned.startsWith("+")) {
            pattern.findAll(cleaned).forEach { match ->
                val number = match.value
                // Only numbers starting with + and having 7+ digits
                if (number.startsWith("+") && number.count { it.isDigit() } >= 7) {
                    phoneNumbers.add(PhoneNumberEntry(number = number))
                }
            }
        }
    }
    
    return phoneNumbers.distinctBy { it.number }
}

private fun savePhoneNumbers(context: Context, numbers: List<PhoneNumberEntry>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val numbersString = numbers.joinToString("|") { it.number }
    prefs.edit().putString(PREFS_KEY, numbersString).apply()
}

private fun loadPhoneNumbers(context: Context): List<PhoneNumberEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val numbersString = prefs.getString(PREFS_KEY, null)
    if (numbersString != null && numbersString.isNotEmpty()) {
        try {
            return numbersString.split("|")
                .filter { it.isNotEmpty() }
                .map { PhoneNumberEntry(number = it) }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    return emptyList()
}

private fun clearSavedPhoneNumbers(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(PREFS_KEY).apply()
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceId = "${context.packageName}/${WhatsAppAutoSendService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains(serviceId, ignoreCase = true) ?: false
}



// Save to VCF function
private fun saveToVcf(contactList: List<String>, context: Context) {
    if (contactList.isEmpty()) {
        Toast.makeText(context, "Contact list is empty", Toast.LENGTH_SHORT).show()
        return
    }

    val vcfContent = StringBuilder()
    contactList.forEach { contactString ->
        val parts = contactString.split(",", limit = 2)
        val name = parts.getOrNull(0)?.trim() ?: ""
        val number = parts.getOrNull(1)?.trim() ?: ""
        if (name.isNotEmpty() && number.isNotEmpty()) {
            vcfContent.append("BEGIN:VCARD\n")
            vcfContent.append("VERSION:2.1\n")
            vcfContent.append("FN:$name\n")
            vcfContent.append("TEL;CELL:$number\n")
            vcfContent.append("END:VCARD\n")
        }
    }

    try {
        // Create folder structure: Documents/ChatsPromo/Contacts/VCF
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val chatsPromoDir = File(documentsDir, "ChatsPromo")
        val contactsDir = File(chatsPromoDir, "Contacts")
        val vcfDir = File(contactsDir, "VCF")
        
        if (!vcfDir.exists()) {
            vcfDir.mkdirs()
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Contacts_$timeStamp.vcf"
        val file = File(vcfDir, fileName)
        file.writeText(vcfContent.toString())

        val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save VCF File"))
        Toast.makeText(context, "VCF file created successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("TextExtractActivity", "Error saving VCF", e)
        Toast.makeText(context, "Error creating VCF file", Toast.LENGTH_SHORT).show()
    }
}

// Save to CSV function
private fun saveToCsv(contactList: List<String>, context: Context) {
    if (contactList.isEmpty()) {
        Toast.makeText(context, "Contact list is empty", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Create folder structure: Documents/ChatsPromo/Contacts/CSV
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val chatsPromoDir = File(documentsDir, "ChatsPromo")
        val contactsDir = File(chatsPromoDir, "Contacts")
        val csvDir = File(contactsDir, "CSV")
        
        if (!csvDir.exists()) {
            csvDir.mkdirs()
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val csvFileName = "Contacts_$timeStamp.csv"
        val file = File(csvDir, csvFileName)

        file.bufferedWriter().use { writer ->
            writer.write("Name,Number\n")
            contactList.forEach { contactString ->
                val parts = contactString.split(",", limit = 2)
                val name = parts.getOrNull(0)?.trim() ?: ""
                val number = parts.getOrNull(1)?.trim() ?: ""
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    writer.write("$name,$number\n")
                }
            }
        }

        val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
        Toast.makeText(context, "CSV file created successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("TextExtractActivity", "Error saving CSV", e)
        Toast.makeText(context, "Error creating CSV file", Toast.LENGTH_SHORT).show()
    }
}
