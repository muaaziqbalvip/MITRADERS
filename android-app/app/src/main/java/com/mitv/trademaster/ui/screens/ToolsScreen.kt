@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.model.JournalEntry
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ToolTab { JOURNAL, RISK_CALC, POSITION_CALC }

@Composable
fun ToolsScreen(language: String) {
    var tab by remember { mutableStateOf(ToolTab.JOURNAL) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Handyman, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (language == "ur") "ٹریڈنگ ٹولز" else "Trading Tools", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(PanelDark, RoundedCornerShape(12.dp)).padding(4.dp)) {
            ToolTabChip(if (language == "ur") "جرنل" else "Journal", Icons.Filled.MenuBook, tab == ToolTab.JOURNAL, Modifier.weight(1f)) { tapFeedback(); tab = ToolTab.JOURNAL }
            ToolTabChip(if (language == "ur") "رسک" else "Risk", Icons.Filled.Warning, tab == ToolTab.RISK_CALC, Modifier.weight(1f)) { tapFeedback(); tab = ToolTab.RISK_CALC }
            ToolTabChip(if (language == "ur") "پوزیشن" else "Position", Icons.Filled.Calculate, tab == ToolTab.POSITION_CALC, Modifier.weight(1f)) { tapFeedback(); tab = ToolTab.POSITION_CALC }
        }

        Spacer(Modifier.height(4.dp))

        when (tab) {
            ToolTab.JOURNAL -> TradingJournalTab(language)
            ToolTab.RISK_CALC -> RiskCalculatorTab(language)
            ToolTab.POSITION_CALC -> PositionSizeCalculatorTab(language)
        }
    }
}

@Composable
private fun ToolTabChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) BrandGreen.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) BrandGreen else BrandSilverDim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// =========================================================================
// TRADING JOURNAL
// =========================================================================

@Composable
private fun TradingJournalTab(language: String) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val repo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val uid = authRepo.currentUser?.uid ?: ""

    fun reload() {
        scope.launch {
            isLoading = true
            entries = try { withContext(Dispatchers.IO) { repo.getJournalEntries(uid) } } catch (e: Exception) { emptyList() }
            isLoading = false
        }
    }

    LaunchedEffect(uid) { if (uid.isNotBlank()) reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(24.dp))
                }
            } else if (entries.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (language == "ur") "ابھی تک کوئی ٹریڈ لاگ نہیں — نیچے دیے گئے + بٹن سے پہلی اندراج شامل کریں"
                        else "No trades logged yet — tap + below to add your first entry",
                        color = BrandSilverDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val wins = entries.count { it.outcome == "win" }
                val losses = entries.count { it.outcome == "loss" }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    JournalStatCard(Modifier.weight(1f), if (language == "ur") "کل ٹریڈز" else "Total", entries.size.toString(), BrandSilver)
                    JournalStatCard(Modifier.weight(1f), if (language == "ur") "جیت" else "Wins", wins.toString(), BrandGreen)
                    JournalStatCard(Modifier.weight(1f), if (language == "ur") "ہار" else "Losses", losses.toString(), BrandRed)
                }

                entries.forEach { entry ->
                    JournalEntryCard(entry, language) {
                        scope.launch {
                            try { withContext(Dispatchers.IO) { repo.deleteJournalEntry(uid, entry.id) } } catch (e: Exception) {}
                            reload()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = { tapFeedback(); showAddSheet = true },
            containerColor = BrandGreen, contentColor = Color(0xFF04120B),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = null) }
    }

    if (showAddSheet) {
        AddJournalEntrySheet(
            language = language,
            onDismiss = { showAddSheet = false },
            onSave = { entry ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repo.addJournalEntry(entry.copy(uid = uid, timestamp = System.currentTimeMillis()))
                            repo.incrementJournalEntriesLogged(uid)
                        }
                    } catch (e: Exception) { }
                    showAddSheet = false
                    reload()
                }
            }
        )
    }
}

@Composable
private fun JournalStatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry, language: String, onDelete: () -> Unit) {
    val outcomeColor = when (entry.outcome) {
        "win" -> BrandGreen
        "loss" -> BrandRed
        else -> Color(0xFFE3B934)
    }
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.direction == "buy") Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = null, tint = if (entry.direction == "buy") BrandGreen else BrandRed, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(entry.pair.ifBlank { "—" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.background(outcomeColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(entry.outcome.uppercase(), color = outcomeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text((if (language == "ur") "داخلہ: " else "Entry: ") + entry.entryPrice.ifBlank { "—" }, color = BrandSilverDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text((if (language == "ur") "خروج: " else "Exit: ") + entry.exitPrice.ifBlank { "—" }, color = BrandSilverDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (entry.profitLoss.isNotBlank()) Text(entry.profitLoss, color = outcomeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(entry.notes, color = BrandSilverDim, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
            if (entry.emotionTag.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.background(BrandSilverDim.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(entry.emotionTag.replaceFirstChar { it.uppercase() }, color = BrandSilverDim, fontSize = 9.5.sp)
                }
            }
        }
    }
}

@Composable
private fun AddJournalEntrySheet(language: String, onDismiss: () -> Unit, onSave: (JournalEntry) -> Unit) {
    var pair by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("buy") }
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var profitLoss by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("win") }
    var notes by remember { mutableStateOf("") }
    var emotion by remember { mutableStateOf("neutral") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = PanelDark) {
            Column(modifier = Modifier.padding(20.dp).width(340.dp).verticalScroll(rememberScrollState())) {
                Text(if (language == "ur") "نئی ٹریڈ لاگ کریں" else "Log New Trade", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                JournalField(pair, { pair = it }, if (language == "ur") "کرنسی جوڑا (مثلاً EUR/USD)" else "Pair (e.g. EUR/USD)")
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(if (language == "ur") "خرید" else "Buy", direction == "buy", Modifier.weight(1f)) { direction = "buy" }
                    ToggleChip(if (language == "ur") "فروخت" else "Sell", direction == "sell", Modifier.weight(1f)) { direction = "sell" }
                }
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JournalField(entryPrice, { entryPrice = it }, if (language == "ur") "داخلہ قیمت" else "Entry Price", Modifier.weight(1f), KeyboardType.Decimal)
                    JournalField(exitPrice, { exitPrice = it }, if (language == "ur") "خروج قیمت" else "Exit Price", Modifier.weight(1f), KeyboardType.Decimal)
                }
                Spacer(Modifier.height(10.dp))
                JournalField(profitLoss, { profitLoss = it }, if (language == "ur") "نفع/نقصان (مثلاً +$25)" else "Profit/Loss (e.g. +$25)")
                Spacer(Modifier.height(10.dp))

                Text(if (language == "ur") "نتیجہ" else "Outcome", color = BrandSilverDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(if (language == "ur") "جیت" else "Win", outcome == "win", Modifier.weight(1f)) { outcome = "win" }
                    ToggleChip(if (language == "ur") "ہار" else "Loss", outcome == "loss", Modifier.weight(1f)) { outcome = "loss" }
                    ToggleChip(if (language == "ur") "برابر" else "B/E", outcome == "breakeven", Modifier.weight(1f)) { outcome = "breakeven" }
                }
                Spacer(Modifier.height(10.dp))

                Text(if (language == "ur") "جذباتی حالت" else "Emotional State", color = BrandSilverDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("confident", "disciplined", "anxious", "fomo", "revenge", "neutral").forEach { tag ->
                        ToggleChip(tag.replaceFirstChar { it.uppercase() }, emotion == tag, Modifier) { emotion = tag }
                    }
                }
                Spacer(Modifier.height(10.dp))

                JournalField(notes, { notes = it }, if (language == "ur") "نوٹس (اختیاری)" else "Notes (optional)", singleLine = false)
                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilverDim),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                    ) { Text(if (language == "ur") "منسوخ" else "Cancel") }
                    Button(
                        onClick = {
                            onSave(JournalEntry(pair = pair, direction = direction, entryPrice = entryPrice, exitPrice = exitPrice, profitLoss = profitLoss, outcome = outcome, notes = notes, emotionTag = emotion))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                    ) { Text(if (language == "ur") "محفوظ کریں" else "Save", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun JournalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = BrandSilverDim, fontSize = 11.sp) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGreen, unfocusedBorderColor = LineSubtle, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BrandGreen),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) BrandGreen.copy(alpha = 0.18f) else PanelDarker)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// =========================================================================
// RISK CALCULATOR
// =========================================================================

@Composable
private fun RiskCalculatorTab(language: String) {
    var accountBalance by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("2") }
    var stopLossPips by remember { mutableStateOf("") }
    var pipValue by remember { mutableStateOf("10") }

    val balance = accountBalance.toFloatOrNull()
    val risk = riskPercent.toFloatOrNull()
    val slPips = stopLossPips.toFloatOrNull()
    val pipVal = pipValue.toFloatOrNull()

    val riskAmount = if (balance != null && risk != null) balance * (risk / 100f) else null
    val maxLotSize = if (riskAmount != null && slPips != null && slPips > 0 && pipVal != null && pipVal > 0) riskAmount / (slPips * pipVal) else null

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (language == "ur") "معلوم کریں کہ آپ کے اکاؤنٹ کے سائز اور رسک فیصد کی بنیاد پر ایک ٹریڈ میں کتنی رقم داؤ پر لگانی چاہیے۔"
            else "Find out how much you should risk on a single trade based on your account size and risk percentage.",
            color = BrandSilverDim, fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        JournalField(accountBalance, { accountBalance = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "اکاؤنٹ بیلنس ($)" else "Account Balance ($)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        JournalField(riskPercent, { riskPercent = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "فی ٹریڈ رسک (%)" else "Risk per Trade (%)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        JournalField(stopLossPips, { stopLossPips = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "اسٹاپ لاس (پپس)" else "Stop Loss (pips)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        JournalField(pipValue, { pipValue = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "پپ ویلیو ($ فی لاٹ)" else "Pip Value ($ per lot)", keyboardType = KeyboardType.Decimal)

        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                ResultRow(if (language == "ur") "زیادہ سے زیادہ رسک رقم" else "Max Risk Amount", riskAmount?.let { "$${"%.2f".format(it)}" } ?: "—")
                Spacer(Modifier.height(10.dp))
                ResultRow(if (language == "ur") "تجویز کردہ لاٹ سائز" else "Suggested Lot Size", maxLotSize?.let { "%.2f".format(it) } ?: "—")
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (language == "ur") "یہ ایک تعلیمی حساب ہے۔ ہمیشہ اپنے بروکر کے مخصوص لاٹ اور مارجن قوانین چیک کریں۔"
            else "This is an educational calculation. Always check your specific broker's lot and margin rules.",
            color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(60.dp))
    }
}

// =========================================================================
// POSITION SIZE CALCULATOR
// =========================================================================

@Composable
private fun PositionSizeCalculatorTab(language: String) {
    var accountBalance by remember { mutableStateOf("") }
    var riskAmount by remember { mutableStateOf("") }
    var entryPrice by remember { mutableStateOf("") }
    var stopLossPrice by remember { mutableStateOf("") }

    val risk = riskAmount.toFloatOrNull()
    val entry = entryPrice.toFloatOrNull()
    val stopLoss = stopLossPrice.toFloatOrNull()

    val priceDistance = if (entry != null && stopLoss != null) kotlin.math.abs(entry - stopLoss) else null
    val positionSize = if (risk != null && priceDistance != null && priceDistance > 0) risk / priceDistance else null
    val positionValue = if (positionSize != null && entry != null) positionSize * entry else null

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (language == "ur") "اپنے رسک، داخلہ قیمت اور اسٹاپ لاس کی بنیاد پر مناسب پوزیشن سائز کا حساب لگائیں۔"
            else "Calculate the right position size based on your risk, entry price, and stop loss.",
            color = BrandSilverDim, fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        JournalField(riskAmount, { riskAmount = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "رسک کرنے کی رقم ($)" else "Amount to Risk ($)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        JournalField(entryPrice, { entryPrice = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "داخلہ قیمت" else "Entry Price", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        JournalField(stopLossPrice, { stopLossPrice = it.filter { c -> c.isDigit() || c == '.' } }, if (language == "ur") "اسٹاپ لاس قیمت" else "Stop Loss Price", keyboardType = KeyboardType.Decimal)

        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                ResultRow(if (language == "ur") "پوزیشن سائز (یونٹس)" else "Position Size (units)", positionSize?.let { "%.2f".format(it) } ?: "—")
                Spacer(Modifier.height(10.dp))
                ResultRow(if (language == "ur") "پوزیشن کی مالیت" else "Position Value", positionValue?.let { "$${"%.2f".format(it)}" } ?: "—")
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (language == "ur") "یہ ایک تعلیمی حساب ہے، ضمانت نہیں۔ اپنا رسک خود سنبھالیں۔"
            else "This is an educational calculation, not a guarantee. Manage your own risk.",
            color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BrandSilverDim, fontSize = 12.sp)
        Text(value, color = BrandGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

