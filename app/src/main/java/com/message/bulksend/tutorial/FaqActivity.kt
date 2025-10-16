package com.message.bulksend.tutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class FaqActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaqTheme {
                FaqScreen(onBackPressed = { finish() })
            }
        }
    }
}

@Composable
fun FaqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF4F46E5),
            background = Color(0xFFF8FAFC),
            surface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBackPressed: () -> Unit) {
    val faqList = remember { getFaqList() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Frequently Asked Questions",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Complete guide to using the app",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4F46E5)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(faqList) { index, faqItem ->
                FaqItemCard(faqItem = faqItem)
            }
            
            // Bottom spacing for edge-to-edge
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FaqItemCard(faqItem: FaqItem) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Question Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faqItem.question,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle)
                )
            }
            
            // Answer with Animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = Color(0xFFE2E8F0),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = faqItem.answer,
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

data class FaqItem(
    val question: String,
    val answer: String
)

fun getFaqList(): List<FaqItem> {
    return listOf(
        FaqItem(
            "How do I use this app?",
            "To use this app, first select contacts from your list, then type your message and press the send button. The app will automatically send messages through WhatsApp to all selected contacts."
        ),
        FaqItem(
            "How do I send bulk messages?",
            "1. Go to the main screen and select 'Bulk Send' option\n2. Select multiple contacts from your contacts list\n3. Choose a message template or write a new message\n4. Press the 'Start Campaign' button\n5. The app will automatically send messages to everyone"
        ),
        FaqItem(
            "What is Accessibility Service?",
            "Accessibility Service gives the app permission to interact with WhatsApp. This allows the app to automatically send messages. Enable it by going to Settings > Accessibility > BulkSend and turning it on."
        ),
        FaqItem(
            "How do I import contacts?",
            "Go to the Contacts tab and press the 'Import Contacts' button. The app will automatically load contacts from your phone. You can also create groups to organize your contacts."
        ),
        FaqItem(
            "How do I create templates?",
            "Go to the Templates section and click 'Create New Template'. Write your message and save it. You can quickly use this template later for sending messages."
        ),
        FaqItem(
            "How do I check campaign status?",
            "After starting a campaign, the 'Campaign Status' screen will automatically open. There you can see how many messages were sent, are pending, or failed."
        ),
        FaqItem(
            "How do I send media files?",
            "While composing a message, click the attachment icon. Select an image or video. This file will be sent to all selected contacts along with your message."
        ),
        FaqItem(
            "The app is crashing, what should I do?",
            "1. Force stop the app\n2. Clear the app cache\n3. Check permissions (Contacts, Storage, Overlay)\n4. Restart your phone\n5. If the problem continues, reinstall the app"
        ),
        FaqItem(
            "What are the premium features?",
            "The premium version includes unlimited messages, advanced scheduling, detailed analytics, and priority support. You can subscribe from the 'Get Premium' section."
        ),
        FaqItem(
            "How do I schedule messages?",
            "During campaign setup, select the 'Schedule' option. Choose the date and time you want the messages to be sent. Messages will be automatically sent at that scheduled time."
        )
    )
}
