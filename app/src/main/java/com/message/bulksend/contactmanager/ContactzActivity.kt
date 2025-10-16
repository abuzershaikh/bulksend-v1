package com.message.bulksend.contactmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.message.bulksend.auth.UserProfileActivity

import com.message.bulksend.utils.ContactLimitHandler
import com.message.bulksend.utils.PremiumUpgradeDialog
import com.message.bulksend.utils.SubscriptionUtils
import kotlinx.coroutines.launch


class ContactzActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                ManageContactsScreen()
            }
        }
    }
}

@Composable
fun ContactsTheme(content: @Composable () -> Unit) {
    // Impressive dark theme with vibrant colors
    val colors = darkColorScheme(
        primary = Color(0xFF7C4DFF), // Vibrant Purple
        primaryContainer = Color(0xFF9E7CFF), // Lighter Purple
        secondary = Color(0xFF00E676), // Vibrant Green
        secondaryContainer = Color(0xFF69F0AE), // Lighter Green
        tertiary = Color(0xFF00B0FF), // Vibrant Blue
        tertiaryContainer = Color(0xFF40C4FF), // Lighter Blue
        surface = Color(0xFF1E1E2E), // Dark Navy
        surfaceVariant = Color(0xFF28293D), // Slightly Lighter Navy
        background = Color(0xFF121212), // Very Dark Blue-Black
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onTertiary = Color.White,
        onSurface = Color(0xFFE0E0FF), // Light Blue-White
        onBackground = Color(0xFFE0E0FF), // Light Blue-White
        outline = Color(0xFF5C6BC0), // Indigo
        outlineVariant = Color(0xFF7986CB), // Light Indigo
        error = Color(0xFFFF5252), // Vibrant Red
        onError = Color.White,
        scrim = Color(0xFF000000) // Black
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        ),
        content = content
    )
}

// Data class to hold information for each import option
data class ImportOptionInfo(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageContactsScreen() {
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)
    val scope = rememberCoroutineScope()
    val contactsRepository = remember { ContactsRepository(context) }

    val savedGroups by contactsRepository.loadGroups().collectAsState(initial = emptyList())

    var importedContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var showPasteTextDialog by remember { mutableStateOf(false) }
    var showSheetsLinkDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var allWhatsAppContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var totalDeviceContacts by remember { mutableStateOf(0) }

    // Premium Dialog States
    var showPremiumDialog by remember { mutableStateOf(false) }
    var currentContactCount by remember { mutableStateOf(0) }
    var contactLimit by remember { mutableStateOf(10) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            totalDeviceContacts = contactsRepository.getTotalContactsCount()
            allWhatsAppContacts = contactsRepository.getWhatsAppContacts()
            if (allWhatsAppContacts.isNotEmpty()) {
                showContactPicker = true
            } else {
                Toast.makeText(context, "No WhatsApp contacts found.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Read Contacts permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = uri.lastPathSegment?.lowercase() ?: ""
            val contacts = when {
                mimeType == "text/csv" || fileName.endsWith(".csv") -> contactsRepository.parseCsv(context, uri)
                mimeType == "text/x-vcard" || fileName.endsWith(".vcf") -> contactsRepository.parseVcf(context, uri)
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || fileName.endsWith(".xlsx") -> contactsRepository.parseXlsx(uri)
                mimeType == "text/comma-separated-values" -> contactsRepository.parseCsv(context, uri)
                else -> {
                    Toast.makeText(context, "Unsupported file type. Please select CSV, VCF, or XLSX files.", Toast.LENGTH_LONG).show()
                    emptyList()
                }
            }
            if (contacts.isNotEmpty()) {
                importedContacts = contacts
                showGroupNameDialog = true
            } else if (contacts.isEmpty() && mimeType != null) {
                Toast.makeText(context, "No valid contacts found in the selected file.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importOptions = listOf(
        ImportOptionInfo(Icons.Default.Description, "CSV", MaterialTheme.colorScheme.primary) {
            filePickerLauncher.launch("*/*")
        },
        ImportOptionInfo(Icons.Default.ContactPage, "VCF", MaterialTheme.colorScheme.secondary) {
            filePickerLauncher.launch("*/*")
        },
        ImportOptionInfo(Icons.Default.TableView, "XLSX", MaterialTheme.colorScheme.tertiary) {
            filePickerLauncher.launch("*/*")
        },
        ImportOptionInfo(Icons.Default.GridOn, "Sheets", Color(0xFF34A853)) {
            showSheetsLinkDialog = true
        },
        ImportOptionInfo(Icons.Default.ContentPaste, "Text", Color(0xFFFF9800)) {
            showPasteTextDialog = true
        },
        ImportOptionInfo(Icons.Default.Contacts, "Phone", Color(0xFFE91E63)) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) -> {
                    totalDeviceContacts = contactsRepository.getTotalContactsCount()
                    allWhatsAppContacts = contactsRepository.getWhatsAppContacts()
                    if (allWhatsAppContacts.isNotEmpty()) {
                        showContactPicker = true
                    } else {
                        Toast.makeText(context, "No WhatsApp contacts found.", Toast.LENGTH_LONG).show()
                    }
                }
                else -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Contact Groups",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImportSourcesCard(importOptions = importOptions)
            }
            item {
                Text(
                    "Saved Groups",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            if (savedGroups.isEmpty()) {
                item {
                    EmptyState(
                        message = "No groups yet!",
                        subtitle = "Use the options above to import contacts and create your first group.",
                        icon = Icons.Default.Groups
                    )
                }
            } else {
                items(savedGroups, key = { it.id }) { group ->
                    ContactGroupCard(
                        group = group,
                        onDelete = {
                            scope.launch {
                                contactsRepository.deleteGroup(group.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showContactPicker) {
        WhatsAppContactPickerScreen(
            totalDeviceContacts = totalDeviceContacts,
            contacts = allWhatsAppContacts,
            onDismiss = { showContactPicker = false },
            onImport = { selectedContacts ->
                showContactPicker = false
                if (selectedContacts.isNotEmpty()) {
                    importedContacts = selectedContacts
                    showGroupNameDialog = true
                }
            }
        )
    }

    if (showGroupNameDialog) {
        GroupNameDialog(
            contactCount = importedContacts.size,
            onDismiss = { showGroupNameDialog = false },
            onConfirm = { groupName ->
                scope.launch {
                    ContactLimitHandler.saveGroupWithLimitCheck(
                        context = context,
                        repository = contactsRepository,
                        groupName = groupName,
                        contacts = importedContacts,
                        onSuccess = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            showGroupNameDialog = false
                            importedContacts = emptyList()
                        },
                        onLimitReached = { current, limit ->
                            currentContactCount = current
                            contactLimit = limit
                            showGroupNameDialog = false
                            showPremiumDialog = true
                        },
                        onPartialSave = { saved, skipped ->
                            Toast.makeText(
                                context,
                                "✅ Saved $saved contacts\n⚠️ Skipped $skipped contacts (limit reached)\n\n💎 Upgrade to Premium for unlimited!",
                                Toast.LENGTH_LONG
                            ).show()
                            showGroupNameDialog = false
                            importedContacts = emptyList()
                        }
                    )
                }
            }
        )
    }

    if (showPasteTextDialog) {
        PasteTextDialog(
            onDismiss = { showPasteTextDialog = false },
            onConfirm = { text ->
                val contacts = contactsRepository.parseCommaSeparatedText(text)
                if (contacts.isNotEmpty()) {
                    importedContacts = contacts
                    showPasteTextDialog = false
                    showGroupNameDialog = true
                } else {
                    Toast.makeText(context, "No valid contacts found", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSheetsLinkDialog) {
        SheetsLinkDialog(
            onDismiss = { showSheetsLinkDialog = false },
            onConfirm = { link ->
                scope.launch {
                    try {
                        val contacts = contactsRepository.fetchFromGoogleSheets(link)
                        if (contacts.isNotEmpty()) {
                            importedContacts = contacts
                            showSheetsLinkDialog = false
                            showGroupNameDialog = true
                        } else {
                            Toast.makeText(context, "No valid contacts found in the sheet", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error fetching from Google Sheets: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Premium Dialog - Shows when contact limit is reached
    if (showPremiumDialog) {
        PremiumUpgradeDialog(
            currentContacts = currentContactCount,
            contactsLimit = contactLimit,
            onDismiss = {
                showPremiumDialog = false
                importedContacts = emptyList()
            },
            onUpgrade = {
                try {
                    val intent = Intent(context, UserProfileActivity::class.java)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Admin panel not available", Toast.LENGTH_SHORT).show()
                }
                showPremiumDialog = false
                importedContacts = emptyList()
            }
        )
    }
}

@Composable
fun ImportSourcesCard(importOptions: List<ImportOptionInfo>) {
    // Create a vibrant gradient for the card background
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    // Create a vibrant gradient for the border
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = borderGradient,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .background(cardGradient)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Import Contacts From",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // New icon layout with improved design
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    importOptions.take(3).forEach { option ->
                        ImportOption(
                            icon = option.icon,
                            label = option.label,
                            color = option.color,
                            onClick = option.onClick
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    importOptions.drop(3).forEach { option ->
                        ImportOption(
                            icon = option.icon,
                            label = option.label,
                            color = option.color,
                            onClick = option.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Create a gradient for the icon background
    val iconGradient = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = 0.3f),
            color.copy(alpha = 0.1f)
        ),
        center = Offset(50f, 50f),
        radius = 100f
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .scale(scale)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(iconGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ContactGroupCard(group: Group, onDelete: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "rotation"
    )

    // Create a gradient for the card
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.background(cardGradient)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Create a gradient background for the icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                ),
                                center = Offset(20f, 20f),
                                radius = 40f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${group.contacts.size} contacts",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete Group",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(rotationAngle),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Divider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    if (group.contacts.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
                            items(group.contacts) { contact ->
                                ContactListItem(contact = contact)
                            }
                        }
                    } else {
                        Text(
                            "This group is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactListItem(contact: Contact) {
    // Create a gradient for the contact item
    val itemGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        ),
        start = Offset.Zero,
        end = Offset(300f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemGradient)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        center = Offset(12f, 12f),
                        radius = 24f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PersonPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                contact.name,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Removed the "+" prefix as requested
            Text(
                contact.number,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.weight(1f))
        if(contact.isWhatsApp) {
            AssistChip(
                onClick = { /*TODO*/ },
                label = {
                    Text(
                        "WhatsApp",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Chat,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    labelColor = MaterialTheme.colorScheme.onSecondary,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                border = null
            )
        }
    }
}

@Composable
fun EmptyState(message: String, subtitle: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp)
    ) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        ),
                        center = Offset(32f, 32f),
                        radius = 64f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Empty State",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GroupNameDialog(
    contactCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create New Group",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "$contactCount contacts found. Enter a name for this group.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name", color = MaterialTheme.colorScheme.onSurface) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(groupName) },
                enabled = groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun PasteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Paste Contacts",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "Paste comma-separated text. Each line should contain a name and number (e.g., John, 1234567890).",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Paste here", color = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SheetsLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = null,
                    tint = Color(0xFF34A853),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Google Sheets Link",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "Paste your Google Sheets link. Make sure the sheet is publicly accessible and has Name in column A and Phone Number in column B.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Google Sheets URL", color = MaterialTheme.colorScheme.onSurface) },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(link) },
                enabled = link.isNotBlank() && link.contains("docs.google.com/spreadsheets"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34A853),
                    contentColor = Color.White
                )
            ) {
                Text("Fetch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppContactPickerScreen(
    totalDeviceContacts: Int,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onImport: (List<Contact>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Use phone number as key for better performance with large lists
    var selectedContactNumbers by remember { mutableStateOf(emptySet<String>()) }
    
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.number.contains(searchQuery)
            }
        }
    }
    
    val isAllSelected = remember(selectedContactNumbers, filteredContacts) {
        filteredContacts.isNotEmpty() && 
        filteredContacts.all { it.number in selectedContactNumbers }
    }

    // Create a gradient for the top bar
    val topBarGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Select WhatsApp Contacts",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val selected = contacts.filter { it.number in selectedContactNumbers }
                            onImport(selected)
                        },
                        enabled = selectedContactNumbers.isNotEmpty()
                    ) {
                        Text(
                            "Import (${selectedContactNumbers.size})",
                            color = if (selectedContactNumbers.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ContactSummaryCard(
                totalDeviceContacts = totalDeviceContacts,
                whatsAppContactsCount = contacts.size
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search contacts...", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = {
                            selectedContactNumbers = if (it) {
                                selectedContactNumbers + filteredContacts.map { c -> c.number }
                            } else {
                                selectedContactNumbers - filteredContacts.map { c -> c.number }.toSet()
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Text(
                        "All",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = filteredContacts.size,
                    key = { index -> filteredContacts[index].number }
                ) { index ->
                    val contact = filteredContacts[index]
                    ContactPickerItem(
                        contact = contact,
                        isSelected = contact.number in selectedContactNumbers,
                        onSelectionChange = {
                            selectedContactNumbers = if (contact.number in selectedContactNumbers) {
                                selectedContactNumbers - contact.number
                            } else {
                                selectedContactNumbers + contact.number
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactPickerItem(
    contact: Contact,
    isSelected: Boolean,
    onSelectionChange: () -> Unit
) {
    // Create a gradient for the contact item
    val itemGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
        ),
        start = Offset.Zero,
        end = Offset(300f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemGradient)
            .clickable(onClick = onSelectionChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelectionChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                contact.name,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                contact.number,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ContactSummaryCard(totalDeviceContacts: Int, whatsAppContactsCount: Int) {
    // Create a gradient for the card
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .background(cardGradient)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                count = totalDeviceContacts,
                label = "Total Contacts",
                icon = Icons.Default.Contacts
            )
            SummaryItem(
                count = whatsAppContactsCount,
                label = "On WhatsApp",
                icon = Icons.Default.Chat
            )
        }
    }
}

@Composable
fun SummaryItem(count: Int, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        center = Offset(24f, 24f),
                        radius = 48f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ManageContactsScreenPreview() {
    ContactsTheme {
        ManageContactsScreen()
    }
}

