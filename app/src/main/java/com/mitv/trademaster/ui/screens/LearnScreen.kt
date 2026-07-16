package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.ui.theme.*

private data class Course(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val level: String,
)

private val courses = listOf(
    Course("Candlestick Basics", "Learn to read candles, wicks, and bodies", Icons.Filled.CandlestickChart, "Beginner"),
    Course("Support & Resistance", "Identify key price zones on any chart", Icons.Filled.Timeline, "Beginner"),
    Course("Trend & Moving Averages", "Understand trend direction with EMAs", Icons.Filled.ShowChart, "Intermediate"),
    Course("Chart Patterns", "Engulfing, doji, pin bars, and more", Icons.Filled.AutoGraph, "Intermediate"),
    Course("Risk Management 101", "Position sizing and protecting your capital", Icons.Filled.Security, "Essential"),
    Course("Trading Psychology", "Discipline, patience, and avoiding revenge trades", Icons.Filled.Psychology, "Essential"),
    Course("Building a Trading Plan", "Rules-based decision making", Icons.Filled.Rule, "Advanced"),
)

@Composable
fun LearnScreen() {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(context.getString(R.string.learn_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(context.getString(R.string.learn_subtitle), color = BrandSilverDim, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(courses) { course ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(BrandGreen.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(course.icon, contentDescription = null, tint = BrandGreen)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(course.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(course.subtitle, color = BrandSilverDim, fontSize = 11.sp)
                        }
                        Text(
                            course.level,
                            color = BrandGreenDim,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(BrandGreen.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
