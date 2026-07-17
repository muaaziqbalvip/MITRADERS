package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.RemoteConfigRepository
import com.mitv.trademaster.data.model.ChatMessage
import com.mitv.trademaster.network.GroqChatClient
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatSupportScreen() {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val configRepo = remember { RemoteConfigRepository() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val uid = authRepo.currentUser?.uid ?: ""
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val listener = if (uid.isNotBlank()) firestoreRepo.listenToChat(uid) { messages = it } else null
        onDispose { listener?.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = BrandGreen)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("AI Assistant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Trading questions & account support", color = BrandSilverDim, fontSize = 11.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Ask me about candlestick patterns, trading concepts,\nyour subscription, or anything about the app.",
                            color = BrandSilverDim, fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp
                        )
                    }
                }
            }
            items(messages) { msg -> ChatBubble(msg) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message...", color = BrandSilverDim, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = LineSubtle,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = BrandGreen,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isBlank() || uid.isBlank() || isSending) return@IconButton
                    val userText = input
                    input = ""
                    isSending = true
                    scope.launch {
                        firestoreRepo.sendChatMessage(uid, ChatMessage(uid = uid, sender = "user", text = userText, timestamp = System.currentTimeMillis()))
                        try {
                            val config = withContext(Dispatchers.IO) { configRepo.getConfig() }
                            if (config.groqApiKey.isBlank()) {
                                firestoreRepo.sendChatMessage(uid, ChatMessage(uid = uid, sender = "ai", text = "Support chat isn't fully set up yet — please reach us on WhatsApp at +${config.whatsappNumber}.", timestamp = System.currentTimeMillis()))
                            } else {
                                val history = messages.takeLast(10).map { (if (it.sender == "user") "user" else "assistant") to it.text }
                                val result = withContext(Dispatchers.IO) { GroqChatClient.sendMessage(config.groqApiKey, history, userText) }
                                val replyText = result.getOrElse { "Sorry, I couldn't process that. Please try again or WhatsApp us at +${config.whatsappNumber}." }
                                firestoreRepo.sendChatMessage(uid, ChatMessage(uid = uid, sender = "ai", text = replyText, timestamp = System.currentTimeMillis()))
                            }
                        } finally {
                            isSending = false
                        }
                    }
                },
                modifier = Modifier.background(BrandGreen, RoundedCornerShape(50)).size(44.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = Color(0xFF04120B))
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) BrandGreen.copy(alpha = 0.15f) else PanelDark,
                    RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
                .widthIn(max = 260.dp)
        ) {
            Text(msg.text, color = if (isUser) BrandGreen else BrandSilver, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
