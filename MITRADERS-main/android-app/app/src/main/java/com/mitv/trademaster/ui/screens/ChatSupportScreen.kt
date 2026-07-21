package com.mitv.trademaster.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.RemoteConfigRepository
import com.mitv.trademaster.data.model.ChatMessage
import com.mitv.trademaster.network.GroqChatClient
import com.mitv.trademaster.network.ImgBBUploader
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ChatSupportScreen(language: String = "en") {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val configRepo = remember { RemoteConfigRepository() }
    val scope = rememberCoroutineScope()
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()
    val listState = rememberLazyListState()

    val uid = authRepo.currentUser?.uid ?: ""
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> pendingImageUri = uri }

    DisposableEffect(uid) {
        val listener = if (uid.isNotBlank()) firestoreRepo.listenToChat(uid) { messages = it } else null
        onDispose { listener?.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendMessage() {
        if ((input.isBlank() && pendingImageUri == null) || uid.isBlank() || isSending) return
        val userText = input
        val imageUri = pendingImageUri
        input = ""
        pendingImageUri = null
        isSending = true

        scope.launch {
            try {
                val config = withContext(Dispatchers.IO) { configRepo.getConfig() }

                var uploadedImageUrl = ""
                if (imageUri != null) {
                    uploadedImageUrl = withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, imageUri) }.getOrElse { "" }
                }

                firestoreRepo.sendChatMessage(
                    uid, ChatMessage(uid = uid, sender = "user", text = userText, imageUrl = uploadedImageUrl, timestamp = System.currentTimeMillis())
                )

                if (config.groqApiKey.isBlank()) {
                    val notSetUp = if (language == "ur") "AI اسسٹنٹ ابھی مکمل طور پر تیار نہیں ہے — براہ کرم واٹس ایپ +${config.whatsappNumber} پر رابطہ کریں۔" else "AI Assistant isn't fully set up yet — please reach us on WhatsApp at +${config.whatsappNumber}."
                    firestoreRepo.sendChatMessage(uid, ChatMessage(uid = uid, sender = "ai", text = notSetUp, timestamp = System.currentTimeMillis()))
                    return@launch
                }

                val history = messages.takeLast(10).map { (if (it.sender == "user") "user" else "assistant") to it.text }

                val result = if (imageUri != null) {
                    val base64 = withContext(Dispatchers.IO) { uriToBase64(context, imageUri) }
                    if (base64 == null) {
                        Result.failure(Exception("Could not read image"))
                    } else {
                        withContext(Dispatchers.IO) { GroqChatClient.sendMessageWithImage(config.groqApiKey, history, userText, base64) }
                    }
                } else {
                    withContext(Dispatchers.IO) { GroqChatClient.sendMessage(config.groqApiKey, history, userText) }
                }

                val replyText = result.getOrElse { err ->
                    val msg = err.message.orEmpty()
                    when {
                        msg.contains("401") || msg.contains("invalid_api_key", ignoreCase = true) ->
                            if (language == "ur") "AI اسسٹنٹ عارضی طور پر دستیاب نہیں (غلط کی) — براہ کرم واٹس ایپ +${config.whatsappNumber} پر رابطہ کریں، ہم براہ راست مدد کریں گے۔"
                            else "AI Assistant is temporarily unavailable (invalid key) — please WhatsApp us at +${config.whatsappNumber} and we'll help directly."
                        msg.contains("429") ->
                            if (language == "ur") "AI اسسٹنٹ اس وقت مصروف ہے — براہ کرم تھوڑی دیر بعد کوشش کریں یا واٹس ایپ +${config.whatsappNumber} پر رابطہ کریں۔"
                            else "AI Assistant is a bit busy right now — please try again in a moment or WhatsApp us at +${config.whatsappNumber}."
                        else ->
                            if (language == "ur") "معذرت، یہ پروسیس نہیں ہو سکا۔ دوبارہ کوشش کریں یا واٹس ایپ +${config.whatsappNumber} پر رابطہ کریں۔"
                            else "Sorry, I couldn't process that. Please try again or WhatsApp us at +${config.whatsappNumber}."
                    }
                }
                firestoreRepo.sendChatMessage(uid, ChatMessage(uid = uid, sender = "ai", text = replyText, timestamp = System.currentTimeMillis()))
            } finally {
                isSending = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandGreen.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (language == "ur") "AI اسسٹنٹ" else "AI Assistant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (language == "ur") "ٹریڈنگ سوالات، چارٹ مدد اور اکاؤنٹ سپورٹ" else "Trading questions, chart help & account support",
                    color = BrandSilverDim, fontSize = 11.sp
                )
            }
        }
        HorizontalDivider(color = LineSubtle)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (language == "ur") "کینڈل اسٹک پیٹرن، ٹریڈنگ تصورات، اپنی سبسکرپشن کے بارے میں پوچھیں، یا تجزیہ کے لیے چارٹ اسکرین شاٹ بھیجیں۔"
                            else "Ask about candlestick patterns, trading concepts, your subscription, or send a chart screenshot for a breakdown.",
                            color = BrandSilverDim, fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp
                        )
                    }
                }
            }
            items(messages) { msg -> ChatBubble(msg) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        pendingImageUri?.let { uri ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(PanelDark)) {
                    AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Text(if (language == "ur") "تصویر منسلک ہے" else "Image attached", color = BrandSilverDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { pendingImageUri = null }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = BrandGreenDim)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        if (language == "ur") "پیغام لکھیں یا چارٹ منسلک کریں..." else "Type a message or attach a chart...",
                        color = BrandSilverDim, fontSize = 13.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen, unfocusedBorderColor = LineSubtle,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BrandGreen,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { tapFeedback(); sendMessage() },
                enabled = !isSending,
                modifier = Modifier.background(BrandGreen, RoundedCornerShape(50)).size(44.dp)
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Send, contentDescription = null, tint = Color(0xFF04120B))
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .background(if (isUser) BrandGreen.copy(alpha = 0.15f) else PanelDark, RoundedCornerShape(14.dp))
                .padding(12.dp)
                .widthIn(max = 260.dp)
        ) {
            if (msg.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = msg.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp)).padding(bottom = if (msg.text.isNotBlank()) 8.dp else 0.dp)
                )
            }
            if (msg.text.isNotBlank()) {
                Text(msg.text, color = if (isUser) BrandGreen else BrandSilver, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

private fun uriToBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        // Downscale large images before sending to keep payload/token size reasonable
        val maxDim = 1024
        val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val outputStream = ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
