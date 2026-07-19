package com.mitv.trademaster.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.BuildConfig
import com.mitv.trademaster.R
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.update.UpdateInfo
import com.mitv.trademaster.update.UpdateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SplashDestination { LOGIN, PROFILE_SETUP, PAYMENT_PENDING, MAIN }

@Composable
fun SplashScreen(
    onFinished: (SplashDestination) -> Unit,
    onUpdateRequired: (UpdateInfo) -> Unit,
) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val updateRepo = remember { UpdateRepository() }
    val scope = rememberCoroutineScope()

    val transition = rememberInfiniteTransition(label = "splash")
    val glow by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    var logoVisible by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(if (logoVisible) 1f else 0.55f, tween(450, easing = FastOutSlowInEasing), label = "scale")
    val logoAlpha by animateFloatAsState(if (logoVisible) 1f else 0f, tween(450), label = "alpha")

    var taglineVisible by remember { mutableStateOf(false) }
    val taglineAlpha by animateFloatAsState(if (taglineVisible) 1f else 0f, tween(350), label = "taglineAlpha")

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(150)
        taglineVisible = true

        // Run the minimum-splash-time animation AND the network checks
        // (update check + auth/profile check) concurrently, not
        // sequentially — whichever finishes last determines total wait,
        // instead of summing both delays.
        coroutineScope {
            val minDisplayTime = async { delay(650) }

            val updateCheck = async {
                try { updateRepo.checkForUpdate(BuildConfig.VERSION_CODE) } catch (e: Exception) { null }
            }

            val destinationCheck = async {
                val user = authRepo.currentUser
                if (user == null) {
                    SplashDestination.LOGIN
                } else {
                    val profile = try { firestoreRepo.getStudentProfile(user.uid) } catch (e: Exception) { null }
                    when {
                        profile == null || profile.studentName.isBlank() -> SplashDestination.PROFILE_SETUP
                        profile.subscriptionStatus != "active" -> SplashDestination.PAYMENT_PENDING
                        else -> SplashDestination.MAIN
                    }
                }
            }

            val results = awaitAll(minDisplayTime, updateCheck, destinationCheck)
            val updateInfo = results[1] as? UpdateInfo
            val destination = results[2] as SplashDestination

            if (updateInfo != null && updateInfo.forceUpdate) {
                onUpdateRequired(updateInfo)
            } else {
                onFinished(destination)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0F171B), Color(0xFF05080A)), radius = 900f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(140.dp).scale(logoScale).alpha(logoAlpha), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(150.dp).alpha(glow).background(
                        Brush.radialGradient(listOf(Color(0xFF34E39A).copy(alpha = 0.3f), Color.Transparent))
                    )
                )
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "MI Trade Master",
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("MI TRADE MASTER", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, modifier = Modifier.alpha(logoAlpha))
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.splash_tagline), color = Color(0xFF34E39A), fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.alpha(taglineAlpha))
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).alpha(taglineAlpha),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { i -> PulsingDot(delayMillis = i * 200) }
        }
    }
}

@Composable
private fun PulsingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = delayMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Box(modifier = Modifier.size(8.dp).scale(scale).clip(CircleShape).background(Color(0xFF34E39A)))
}
