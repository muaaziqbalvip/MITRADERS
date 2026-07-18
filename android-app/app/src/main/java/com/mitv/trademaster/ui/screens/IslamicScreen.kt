package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.SessionState
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private data class HadeesEntry(val arabic: String, val en: String, val ur: String, val source: String)
private data class DuaEntry(val titleEn: String, val titleUr: String, val arabic: String, val transliteration: String, val en: String, val ur: String)

// Curated, source-attributed content about honest trade/business conduct in Islam.
// Kept general and widely-agreed-upon rather than disputed rulings on specific
// modern instruments — this is educational/spiritual content, not a fatwa.
private val tradingHadees = listOf(
    HadeesEntry(
        arabic = "التَّاجِرُ الصَّدُوقُ الْأَمِينُ مَعَ النَّبِيِّينَ وَالصِّدِّيقِينَ وَالشُّهَدَاءِ",
        en = "The truthful, trustworthy merchant will be with the Prophets, the truthful, and the martyrs (on the Day of Judgment).",
        ur = "سچا اور امانت دار تاجر نبیوں، صدیقین اور شہداء کے ساتھ ہوگا۔",
        source = "Tirmidhi 1209"
    ),
    HadeesEntry(
        arabic = "الْبَيِّعَانِ بِالْخِيَارِ مَا لَمْ يَتَفَرَّقَا، فَإِنْ صَدَقَا وَبَيَّنَا بُورِكَ لَهُمَا فِي بَيْعِهِمَا",
        en = "The two parties in a transaction have the right to cancel it as long as they haven't parted. If they are honest and transparent, their transaction is blessed.",
        ur = "دونوں فریق جب تک الگ نہ ہوں، سودا منسوخ کرنے کا اختیار رکھتے ہیں۔ اگر وہ سچ بولیں اور واضح رہیں تو ان کے سودے میں برکت ہوتی ہے۔",
        source = "Sahih al-Bukhari 2079"
    ),
    HadeesEntry(
        arabic = "مَنْ غَشَّنَا فَلَيْسَ مِنَّا",
        en = "Whoever deceives us is not one of us.",
        ur = "جو ہمیں دھوکہ دے وہ ہم میں سے نہیں۔",
        source = "Sahih Muslim 102"
    ),
    HadeesEntry(
        arabic = "إِنَّمَا الْبَيْعُ عَنْ تَرَاضٍ",
        en = "Business transactions are only valid by mutual consent.",
        ur = "تجارت صرف باہمی رضامندی سے جائز ہے۔",
        source = "Ibn Majah 2185"
    ),
)

private val tradingDuas = listOf(
    DuaEntry(
        titleEn = "Before starting any work or trade",
        titleUr = "کسی بھی کام یا تجارت سے پہلے",
        arabic = "بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ",
        transliteration = "Bismillahi tawakkaltu 'ala Allah",
        en = "In the name of Allah, I place my trust in Allah.",
        ur = "اللہ کے نام سے، میں نے اللہ پر بھروسہ کیا۔"
    ),
    DuaEntry(
        titleEn = "For lawful provision (rizq)",
        titleUr = "حلال رزق کے لیے",
        arabic = "اللَّهُمَّ اكْفِنِي بِحَلَالِكَ عَنْ حَرَامِكَ، وَأَغْنِنِي بِفَضْلِكَ عَمَّنْ سِوَاكَ",
        transliteration = "Allahumma-kfini bi halalika 'an haramik, wa aghnini bi fadlika 'amman siwak",
        en = "O Allah, suffice me with what You have made lawful instead of what You have made unlawful, and make me independent of all others besides You.",
        ur = "اے اللہ، مجھے اپنے حلال سے حرام سے بچا لے، اور اپنے فضل سے مجھے دوسروں سے بے نیاز کر دے۔"
    ),
    DuaEntry(
        titleEn = "Seeking ease and blessing in a decision",
        titleUr = "کسی فیصلے میں آسانی اور برکت کے لیے",
        arabic = "اللَّهُمَّ لَا سَهْلَ إِلَّا مَا جَعَلْتَهُ سَهْلًا، وَأَنْتَ تَجْعَلُ الْحَزْنَ إِذَا شِئْتَ سَهْلًا",
        transliteration = "Allahumma la sahla illa ma ja'altahu sahla, wa anta taj'alul-hazna idha shi'ta sahla",
        en = "O Allah, nothing is easy except what You make easy, and You make the difficult easy if You wish.",
        ur = "اے اللہ، کوئی چیز آسان نہیں سوائے اس کے جسے تو آسان کر دے، اور تو مشکل کو چاہے تو آسان کر دیتا ہے۔"
    ),
)

@Composable
fun IslamicScreen(language: String) {
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = SessionState())
    val scope = rememberCoroutineScope()

    var showReligionPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(session.religionAsked) {
        if (!session.religionAsked) showReligionPrompt = true
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(if (language == "ur") "روحانی گوشہ" else "Spiritual Corner", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (language == "ur") "امانت داری اور صبر کے ساتھ رزق کمانا" else "Earning with honesty and patience",
                    color = BrandSilverDim, fontSize = 12.sp
                )
            }

            // ---------- Durood Sharif — only shown once Muslim identity is confirmed ----------
            if (session.religionAsked && session.isMuslim) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFE3B934).copy(alpha = 0.14f), PanelDark)), RoundedCornerShape(18.dp))
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFE3B934))
                                Spacer(Modifier.width(8.dp))
                                Text(if (language == "ur") "درود شریف" else "Durood Sharif", color = Color(0xFFE3B934), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ",
                                color = Color.White, fontSize = 15.sp, lineHeight = 26.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (language == "ur")
                                    "اے اللہ، محمد ﷺ اور آل محمد پر رحمت نازل فرما، جیسے تو نے ابراہیم اور آل ابراہیم پر نازل فرمائی، بے شک تو قابل تعریف اور بزرگ ہے۔"
                                else
                                    "O Allah, send blessings upon Muhammad ﷺ and the family of Muhammad, as You sent blessings upon Ibrahim and the family of Ibrahim. Indeed, You are Praiseworthy and Glorious.",
                                color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (language == "ur") "کام شروع کرنے سے پہلے پڑھنا مستحب ہے۔" else "Recommended to recite before starting your work.",
                                color = Color(0xFFE3B934).copy(alpha = 0.8f), fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // ---------- Duas ----------
            item {
                Text(if (language == "ur") "دعائیں" else "Duas", color = BrandSilverDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            items(tradingDuas) { dua ->
                Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (language == "ur") dua.titleUr else dua.titleEn, color = BrandGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Text(dua.arabic, color = Color.White, fontSize = 17.sp, lineHeight = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(dua.transliteration, color = BrandSilverDim, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Spacer(Modifier.height(8.dp))
                        Text(if (language == "ur") dua.ur else dua.en, color = BrandSilver, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // ---------- Hadees on honest trade ----------
            item {
                Spacer(Modifier.height(4.dp))
                Text(if (language == "ur") "تجارت میں امانت داری پر احادیث" else "Hadees on Honest Trade", color = BrandSilverDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            items(tradingHadees) { h ->
                Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(h.arabic, color = Color.White, fontSize = 15.sp, lineHeight = 26.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (language == "ur") h.ur else h.en, color = BrandSilver, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(h.source, color = BrandSilverDim, fontSize = 10.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(70.dp)) }
        }
    }

    if (showReligionPrompt) {
        AlertDialog(
            onDismissRequest = { /* must choose an option */ },
            containerColor = PanelDark,
            title = { Text(if (language == "ur") "ایک چھوٹا سا سوال" else "A quick question", color = Color.White) },
            text = {
                Text(
                    if (language == "ur")
                        "کیا آپ مسلمان ہیں؟ اس سے ہم آپ کو مناسب مواد دکھا سکیں گے (جیسے درود شریف)۔"
                    else
                        "Are you Muslim? This helps us show you the right content here (like Durood Sharif).",
                    color = BrandSilverDim, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { sessionRepo.setReligionAnswer(true) }
                    showReligionPrompt = false
                }) { Text(if (language == "ur") "جی ہاں" else "Yes", color = BrandGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { sessionRepo.setReligionAnswer(false) }
                    showReligionPrompt = false
                }) { Text(if (language == "ur") "نہیں" else "No", color = BrandSilverDim) }
            }
        )
    }
}
