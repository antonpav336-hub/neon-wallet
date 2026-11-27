package com.neonwallet.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale


@Composable
internal fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF5426A6))), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bolt, null, tint = White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("NEON", color = White, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 18.sp)
            Text("WALLET", color = Purple, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.NotificationsNone, null, tint = White, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun Home(balance: Long, visible: Boolean, toggle: () -> Unit, topup: () -> Unit, transfer: () -> Unit, withdraw: () -> Unit, txs: List<Tx>, openCards: () -> Unit, openHistory: () -> Unit, onTransactionClick: (Tx) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Header() }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ТВОИ ФИНАНСЫ", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Color(0xFF38205B), Color(0xFF21172F), Color(0xFF171220)))).border(1.dp, Color(0xFF7650A5).copy(alpha = .42f), RoundedCornerShape(25.dp)).padding(21.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Основной счёт", color = Color(0xFFC7B9DB), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = toggle, modifier = Modifier.size(28.dp)) { Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = White, modifier = Modifier.size(17.dp)) }
                        }
                        AnimatedContent(targetState = if (visible) money(balance) else "•••••• ₽", transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "balance_visibility") { value ->
                            Text(value, color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                        }
                        Text("Российский рубль · DEMO", color = Color(0xFFC7B9DB), fontSize = 11.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Пополнить", Icons.Default.Add, topup, true, Modifier.weight(1f))
                            ActionButton("Перевести", Icons.Default.Send, transfer, false, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Text("Быстрые действия", Modifier.padding(start = 22.dp, top = 22.dp, bottom = 12.dp), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Перевести", Icons.Default.Send, Purple, transfer, Modifier.weight(1f))
                QuickAction("Пополнить", Icons.Default.AddCard, Green, topup, Modifier.weight(1f))
                QuickAction("Вывести", Icons.Default.SouthWest, Color(0xFFFFB86B), withdraw, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Мои продукты", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Все", Modifier.clickable(onClick = openCards), color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(17.dp)).background(Panel).clickable(onClick = openCards).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Purple.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.CreditCard, null, tint = Purple) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("NEON виртуальная", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("•••• 2048", color = Muted, fontSize = 11.sp) }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Последние операции", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Все", Modifier.clickable(onClick = openHistory), color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(txs.take(5)) { tx -> TransactionRow(tx) { onTransactionClick(tx) } }
    }
}

@Composable
internal fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick = action, modifier = modifier.height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (filled) Purple else Color(0xFF33244C)), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, action: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).clickable { action() }.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Spacer(Modifier.height(9.dp)); Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TransactionRow(tx: Tx, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).animateContentSize().clip(RoundedCornerShape(16.dp)).background(Panel).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (tx.incoming) Green.copy(alpha = .13f) else Purple.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(if (tx.incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (tx.incoming) Green else Purple)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${tx.date} · ${tx.status}", color = Muted, fontSize = 11.sp)
        }
        val signedAmount = when {
            tx.amount > 0 -> "+" + money(tx.amount)
            tx.amount < 0 -> "−" + money(tx.amount)
            else -> money(0)
        }
        Text(signedAmount, color = if (tx.incoming) Green else White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
