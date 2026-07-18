package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private val iconMap: Map<String, ImageVector> = mapOf(
    "School" to Icons.Filled.School,
    "CandlestickChart" to Icons.Filled.CandlestickChart,
    "Timeline" to Icons.Filled.Timeline,
    "ShowChart" to Icons.Filled.ShowChart,
    "AutoGraph" to Icons.Filled.AutoGraph,
    "Security" to Icons.Filled.Security,
    "Psychology" to Icons.Filled.Psychology,
    "Rule" to Icons.Filled.Rule,
)

@Composable
fun CoursesScreen(language: String, onCourseSelected: (Course) -> Unit) {
    val repo = remember { FirestoreRepository() }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            courses = try { repo.getCourses() } catch (e: Exception) { emptyList() }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(if (language == "ur") "ٹریڈنگ سیکھیں" else "Learn Trading", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (language == "ur") "بنیادی سے پرو تک، اپنی رفتار سے" else "From basics to pro, at your own pace",
            color = BrandSilverDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        } else if (courses.isEmpty()) {
            Text(
                if (language == "ur") "ابھی کوئی کورس دستیاب نہیں — جلد شامل کیا جائے گا۔" else "No courses yet — check back soon.",
                color = BrandSilverDim, fontSize = 13.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(courses) { course ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onCourseSelected(course) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(46.dp).background(BrandGreen.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(iconMap[course.iconName] ?: Icons.Filled.School, contentDescription = null, tint = BrandGreen)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(course.description, color = BrandSilverDim, fontSize = 11.sp, maxLines = 1)
                            }
                            Text(
                                course.level.replaceFirstChar { it.uppercase() },
                                color = BrandGreenDim, fontSize = 10.sp,
                                modifier = Modifier.background(BrandGreen.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
