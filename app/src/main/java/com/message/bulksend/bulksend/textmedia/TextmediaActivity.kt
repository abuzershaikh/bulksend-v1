package com.message.bulksend.bulksend.textmedia



import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.bulksend.*
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.Setting
import com.message.bulksend.templates.TemplateActivity
import com.message.bulksend.templates.TemplateRepository
import com.message.bulksend.utils.CampaignAutoSendManager
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import com.message.bulksend.utils.AccessibilityPermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

class TextmediaActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        setContent {
            WhatsAppCampaignTheme {
                TextMediaCampaignManagerScreen()
            }
        }
    }
}

enum class SendOrder {
    TEXT_FIRST, MEDIA_FIRST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMediaCampaignManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val contactsRepository = remember { ContactsRepository(context) }
    val templateRepository = remember { TemplateRepository(context) }

    // States
    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())
    var campaignName by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var message by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp") }
    var campaignProgress by remember { mutableStateOf(0f) }
    var sendingIndex by remember { mutableStateOf(0) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var selectedDelay by remember { mutableStateOf("Fixed (5 sec)") }
    var uniqueIdentityEnabled by remember { mutableStateOf(false) }
    var sendOrder by remember { mutableStateOf(SendOrder.TEXT_FIRST) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var toolInputText by remember { mutableStateOf("") }
    var selectedFancyFont by remember { mutableStateOf("Script") }
    var showCustomDelayDialog by remember { mutableStateOf(false) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var campaignToResumeLoaded by remember { mutableStateOf(false) }

    var isStep1Expanded by remember { mutableStateOf(true) }
    var isStep2Expanded by remember { mutableStateOf(true) }
    var isStep3Expanded by remember { mutableStateOf(true) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf("") }
    var showCountryCodeInfoDialog by remember { mutableStateOf(false) }

    val intent = (context as Activity).intent
    val campaignIdToResumeFromHistory = remember { intent.getStringExtra("CAMPAIGN_ID_TO_RESUME") }

    val progressAnimation by animateFloatAsState(
        targetValue = campaignProgress,
        animationSpec = tween(500),
        label = "progress"
    )
    
    // Setup overlay callbacks with state access
    LaunchedEffect(Unit) {
        val activity = context as? TextmediaActivity
        activity?.overlayManager?.setOnStartCallback {
            // Resume campaign - overlay se start button click hua
            android.util.Log.d("TextmediaActivity", "Campaign resumed from overlay")
        }
        
        activity?.overlayManager?.setOnStopCallback {
            // Pause campaign - overlay se stop button click hua
            android.util.Log.d("TextmediaActivity", "Campaign paused from overlay")
        }
    }

    val templateSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val templateId = result.data?.getStringExtra("SELECTED_TEMPLATE_ID")
            if (templateId != null) {
                val template = templateRepository.getTemplateById(templateId)
                if (template != null) {
                    message = template.message
                    mediaUri = template.mediaUri?.let { Uri.parse(it) }
                    Toast.makeText(context, "Template '${template.name}' loaded!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    mediaUri = uri
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Failed to get permission for media file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val contactzActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Groups will auto-update via Flow
    }

    LaunchedEffect(Unit) {
        val pref = withContext(Dispatchers.IO) { settingDao.getSetting("whatsapp_preference") }
        whatsAppPreference = pref?.value ?: "WhatsApp"
    }

    LaunchedEffect(groups, campaignIdToResumeFromHistory) {
        if (campaignIdToResumeFromHistory != null && groups.isNotEmpty() && !campaignToResumeLoaded) {
            val campaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(campaignIdToResumeFromHistory) }
            if (campaign != null) {
                val group = groups.find { it.id.toString() == campaign.groupId }
                if (group != null) {
                    selectedGroup = group
                    campaignName = campaign.campaignName
                    message = campaign.message
                    // mediaUri cannot be persisted, user must re-select it for security reasons.
                    resumableProgress = campaign
                    campaignStatus = campaign.contactStatuses
                    isStep1Expanded = false
                    campaignToResumeLoaded = true
                } else {
                    Toast.makeText(context, "Campaign group not found for resuming.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Effect for when a group is selected MANUALLY
    LaunchedEffect(selectedGroup) {
        if (campaignIdToResumeFromHistory == null) {
            resumableProgress = null
            campaignStatus = emptyList()
            message = ""
            mediaUri = null
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File + Text Campaign", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)) {
                        Row(
                            modifier = androidx.compose.ui.Modifier
                                .clickable { expanded = !expanded }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                whatsAppPreference,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = androidx.compose.ui.Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("WhatsApp") },
                                onClick = {
                                    whatsAppPreference = "WhatsApp"
                                    expanded = false
                                    scope.launch(Dispatchers.IO) {
                                        settingDao.upsertSetting(Setting("whatsapp_preference", "WhatsApp"))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("WhatsApp Business") },
                                onClick = {
                                    whatsAppPreference = "WhatsApp Business"
                                    expanded = false
                                    scope.launch(Dispatchers.IO) {
                                        settingDao.upsertSetting(Setting("whatsapp_preference", "WhatsApp Business"))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Business, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StepCard(
                    stepNumber = 1,
                    title = "Campaign Setup",
                    icon = Icons.Filled.Campaign,
                    isCompleted = campaignName.isNotBlank() && selectedGroup != null,
                    isExpanded = isStep1Expanded,
                    onHeaderClick = { isStep1Expanded = !isStep1Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Campaign Name *") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it },
                            label = { Text("Country Code (Required)") },
                            placeholder = { Text("e.g., +91 for India") },
                            leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = "Country Code") },
                            trailingIcon = {
                                IconButton(onClick = { showCountryCodeInfoDialog = true }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "Country Code Info")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        GroupSelector(
                            groups = groups,
                            selectedGroup = selectedGroup,
                            onGroupSelected = { selectedGroup = it }
                        )
                        ImportButton(
                            text = "Add/Manage Lists",
                            icon = Icons.Filled.GroupAdd,
                            onClick = {
                                contactzActivityLauncher.launch(Intent(context, ContactzActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                StepCard(
                    stepNumber = 2,
                    title = "Message & Media",
                    icon = Icons.Filled.Message,
                    isCompleted = message.isNotBlank() && mediaUri != null,
                    isExpanded = isStep2Expanded,
                    onHeaderClick = { isStep2Expanded = !isStep2Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    val intent = Intent(context, TemplateActivity::class.java).apply {
                                        putExtra("IS_FOR_SELECTION", true)
                                    }
                                    templateSelectorLauncher.launch(intent)
                                },
                                label = { Text("Use Template", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Style,
                                        contentDescription = "Use Template",
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                        MessageComposerWithTools(
                            value = message,
                            onValueChange = { message = it },
                            activeTool = activeTool,
                            onActiveToolChange = { tool -> activeTool = if (activeTool == tool) null else tool },
                            toolInputText = toolInputText,
                            onToolInputChange = { toolInputText = it },
                            selectedFancyFont = selectedFancyFont,
                            onFancyFontChange = { selectedFancyFont = it }
                        )
                        AttachMediaContent(
                            mediaUri = mediaUri,
                            onAttachClick = { mediaPicker.launch(arrayOf("*/*")) },
                            onRemoveClick = { mediaUri = null }
                        )
                        SendOrderSelector(
                            sendOrder = sendOrder,
                            onOrderChange = { sendOrder = it }
                        )
                    }
                }
            }
            item {
                StepCard(
                    stepNumber = 3,
                    title = "Settings",
                    icon = Icons.Filled.Settings,
                    isCompleted = true,
                    isExpanded = isStep3Expanded,
                    onHeaderClick = { isStep3Expanded = !isStep3Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unique ID per Message")
                            Switch(
                                checked = uniqueIdentityEnabled,
                                onCheckedChange = { uniqueIdentityEnabled = it }
                            )
                        }
                        DelaySelector(
                            selectedDelay = selectedDelay,
                            onDelaySelected = { selectedDelay = it },
                            onCustomClick = { showCustomDelayDialog = true }
                        )
                    }
                }
            }
            item {
                CampaignSummaryCard(
                    campaignName = campaignName,
                    selectedGroup = selectedGroup,
                    message = message,
                    mediaUri = mediaUri,
                    whatsAppPreference = whatsAppPreference,
                    isSending = isSending,
                    progress = progressAnimation,
                    sendingIndex = sendingIndex,
                    campaignStatus = campaignStatus,
                    onLaunchCampaign = { isResuming ->
                        if (campaignName.isBlank() || selectedGroup == null || message.isBlank() || mediaUri == null) {
                            Toast.makeText(context, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                            return@CampaignSummaryCard
                        }

                        // Country code is mandatory
                        if (countryCode.isBlank()) {
                            campaignError = "Country code is required. Please enter a country code (e.g., +91)."
                            return@CampaignSummaryCard
                        }

                        // Check overlay permission first
                        if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
                            showOverlayPermissionDialog = true
                            return@CampaignSummaryCard
                        }

                        if (!isAccessibilityServiceEnabled(context)) {
                            showAccessibilityDialog = true
                            return@CampaignSummaryCard
                        }
                        val packageName = when (whatsAppPreference) {
                            "WhatsApp" -> "com.whatsapp"
                            "WhatsApp Business" -> "com.whatsapp.w4b"
                            else -> null
                        }
                        if (packageName != null && !isPackageInstalled(context, packageName)) {
                            campaignError = "$whatsAppPreference is not installed."
                            return@CampaignSummaryCard
                        }

                        isSending = true
                        scope.launch {
                            val group = selectedGroup!!
                            var campaignStoppedPrematurely = false

                            val campaignToRun = if (isResuming && resumableProgress != null) {
                                resumableProgress!!.copy(isStopped = false, isRunning = true)
                            } else {
                                Campaign(
                                    id = UUID.randomUUID().toString(),
                                    groupId = group.id.toString(),
                                    campaignName = campaignName,
                                    message = message,
                                    timestamp = System.currentTimeMillis(),
                                    totalContacts = group.contacts.size,
                                    contactStatuses = group.contacts.map { ContactStatus(it.number, "pending") },
                                    isStopped = false,
                                    isRunning = true,
                                    campaignType = "TEXTMEDIA" // Set correct campaign type
                                )
                            }
                            currentCampaignId = campaignToRun.id
                            withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                            // Campaign launch hone par auto-send service enable karein
                            CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                            // Start overlay with campaign
                            (context as? TextmediaActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                            val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }

                            try {
                                for (contactStatus in contactsToSend) {
                                    // Check if paused by overlay
                                    while ((context as? TextmediaActivity)?.overlayManager?.isPaused() == true) {
                                        delay(500)
                                    }
                                    
                                    val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                    if (currentState == null || currentState.isStopped) {
                                        campaignStoppedPrematurely = true
                                        break
                                    }
                                    sendingIndex = currentState.sentCount + currentState.failedCount + 1
                                    campaignProgress = sendingIndex.toFloat() / currentState.totalContacts
                                    
                                    // Update overlay progress
                                    (context as? TextmediaActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
                                    
                                    val contact = group.contacts.find { it.number == contactStatus.number } ?: continue
                                    CampaignState.isSendActionSuccessful = null

                                    Toast.makeText(context, "Sending to ${contact.name} ($sendingIndex/${group.contacts.size})", Toast.LENGTH_SHORT).show()

                                    // Add country code if number doesn't start with +
                                    val finalNumber = if (contact.number.startsWith("+")) {
                                        contact.number.replace(Regex("[^\\d+]"), "")
                                    } else {
                                        val cleanCode = countryCode.replace(Regex("[^\\d+]"), "")
                                        val cleanNum = contact.number.replace(Regex("[^\\d]"), "")
                                        "$cleanCode$cleanNum"
                                    }
                                    val cleanNumber = finalNumber.replace("+", "")
                                    val finalMessage = if (uniqueIdentityEnabled) message + "\n\n" + generateRandomString() else message
                                    val encodedMessage = URLEncoder.encode(finalMessage, "UTF-8")

                                    val textIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    val mediaIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, mediaUri)
                                        type = context.contentResolver.getType(mediaUri!!)
                                        putExtra("jid", "$cleanNumber@s.whatsapp.net")
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    if (sendOrder == SendOrder.TEXT_FIRST) {
                                        context.startActivity(textIntent)
                                        delay(4000)
                                        context.startActivity(mediaIntent)
                                    } else { // MEDIA_FIRST
                                        context.startActivity(openChatIntent)
                                        delay(2500)
                                        context.startActivity(mediaIntent)
                                        delay(4000)
                                        context.startActivity(textIntent)
                                    }

                                    // This logic for success/fail needs to be improved like in BulksendActivity
                                    // For now, assuming it succeeds.
                                    withContext(Dispatchers.IO) { campaignDao.updateContactStatus(currentCampaignId!!, contact.number, "sent") }
                                    val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                    if (updatedCampaign != null) campaignStatus = updatedCampaign.contactStatuses


                                    val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                        try { selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000 } catch (e: Exception) { 5000L }
                                    } else if (selectedDelay.startsWith("Random")) {
                                        Random.nextLong(5000, 15001)
                                    } else {
                                        try { selectedDelay.split(" ")[0].toLong() * 1000 } catch (e: Exception) { 5000L }
                                    }
                                    delay(maxOf(3000L, delayMillis))

                                }
                            } finally {
                                val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                if (finalState != null) {
                                    val finishedCampaign = finalState.copy(isRunning = false, isStopped = campaignStoppedPrematurely)
                                    withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }

                                    if (campaignStoppedPrematurely) {
                                        // Campaign stopped, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                        resumableProgress = finishedCampaign
                                        Toast.makeText(context, "Campaign stopped. Progress saved.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Campaign completed, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                        Toast.makeText(context, "Campaign Finished!", Toast.LENGTH_LONG).show()
                                        selectedGroup = null
                                        campaignName = ""
                                        message = ""
                                        mediaUri = null
                                        resumableProgress = null
                                        campaignStatus = emptyList()
                                    }
                                }
                                isSending = false
                                currentCampaignId = null
                            }
                        }
                    },
                    onStartOver = {
                        resumableProgress = null
                        campaignStatus = emptyList()
                        message = ""
                        campaignName = ""
                        selectedGroup = null
                        mediaUri = null
                        Toast.makeText(context, "Cleared. You can start a new campaign.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            if (isSending) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { progressAnimation }, modifier = Modifier.fillMaxWidth())
                        Text("Sending ${sendingIndex} of ${selectedGroup?.contacts?.size ?: 0}...", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Stop/Resume buttons removed - now controlled via overlay only
        }
    }

    // ResumeConfirmationDialog and StopConfirmationDialog removed - controlled via overlay

    if (campaignError != null) {
        AlertDialog(
            onDismissRequest = { campaignError = null },
            title = { Text("Error") },
            text = { Text(campaignError!!) },
            confirmButton = {
                Button(onClick = {
                    if (campaignError!!.contains("Accessibility")) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    campaignError = null
                }) {
                    Text("OK")
                }
            }
        )
    }
    if (showCustomDelayDialog) {
        CustomDelayDialog(
            onDismiss = { showCustomDelayDialog = false },
            onConfirm = { delayInSeconds ->
                if (delayInSeconds >= 3) {
                    selectedDelay = "Custom ($delayInSeconds sec)"
                    showCustomDelayDialog = false
                } else {
                    Toast.makeText(context, "Minimum delay is 3 seconds.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Overlay Permission Dialog
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            icon = { 
                Icon(
                    Icons.Outlined.Layers, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            title = { 
                Text(
                    "Overlay Permission Required",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Overlay permission is required for campaign control.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "The overlay allows you to pause and resume campaigns without opening the app:",
                        fontSize = 14.sp
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("Overlay appears on screen when campaign is running", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("Control campaign with Stop/Start button", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("View real-time progress", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enable 'Display over other apps' permission in Settings.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        com.message.bulksend.overlay.OverlayHelper.requestOverlayPermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("I Agree")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }

    if (showCountryCodeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCountryCodeInfoDialog = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("Country Code Information") },
            text = {
                Text(
                    "📱 Country code is required for all contacts.\n\n" +
                            "Examples:\n" +
                            "• India: +91\n" +
                            "• USA: +1\n" +
                            "• UK: +44\n" +
                            "• UAE: +971\n\n" +
                            "The country code will be added to numbers that don't already have a + prefix."
                )
            },
            confirmButton = {
                Button(onClick = { showCountryCodeInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Accessibility Permission Dialog
    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = {
                Toast.makeText(context, "Please enable permission in Settings", Toast.LENGTH_SHORT).show()
            },
            onDisagree = {
                Toast.makeText(context, "Accessibility permission is required", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAccessibilityDialog = false
            }
        )
    }
}

@Composable
fun SendOrderSelector(
    sendOrder: SendOrder,
    onOrderChange: (SendOrder) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Send Order:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOrderChange(SendOrder.TEXT_FIRST) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = sendOrder == SendOrder.TEXT_FIRST,
                        onClick = { onOrderChange(SendOrder.TEXT_FIRST) }
                    )
                    Text("Text ➔ Media", color = MaterialTheme.colorScheme.onSurface)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOrderChange(SendOrder.MEDIA_FIRST) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = sendOrder == SendOrder.MEDIA_FIRST,
                        onClick = { onOrderChange(SendOrder.MEDIA_FIRST) }
                    )
                    Text("Media ➔ Text", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
