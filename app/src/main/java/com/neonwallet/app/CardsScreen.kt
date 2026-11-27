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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale


@Composable
internal fun Cards(frozen: Boolean, cardLimit: Long, onToggleFreeze: () -> Unit, onLimitChange: (Long) -> Unit, onPay: () -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    var showLimitEditor by remember { mutableStateOf(false) }
    var limitInput by remember(cardLimit) { mutableStateOf(cardLimit.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Text("Мои карты", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Управляй своими картами", Modifier.padding(start = 22.dp, top = 5.dp), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(215.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF65567D), Color(0xFF393143), Color(0xFF201D27))))
            .border(1.dp, Color(0xFFC8B6EA).copy(alpha = .4f), RoundedCornerShape(26.dp))) {
            Image(painterResource(R.drawable.bg_neon_card), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.32f))
            Box(Modifier.align(Alignment.TopEnd).offset(x = 48.dp, y = (-60).dp).size(180.dp).background(Color(0xFFD8C8F5).copy(alpha = .10f), CircleShape).blur(24.dp))
            Column(Modifier.fillMaxHeight().padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NEON WALLET", color = White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Contactless, null, tint = White)
                }
                Spacer(Modifier.weight(1f))
                Text(if (showDetails) "4242  4242  4242  2048" else "••••  ••••  ••••  2048", color = White, fontSize = 19.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(15.dp))
                Row {
                    Column { Text("ВЛАДЕЛЕЦ", color = White.copy(alpha = .65f), fontSize = 9.sp); Text("NEON USER", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f)); Text("VISA", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
            if (frozen) {
                Box(Modifier.fillMaxSize().background(Color(0xAA090711)), contentAlignment = Alignment.Center) {
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xCC171323)).border(1.dp, Color(0xFFFFB86B).copy(alpha = .65f), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFFFFB86B))
                        Spacer(Modifier.width(8.dp))
                        Text("КАРТА ЗАМОРОЖЕНА", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEON виртуальная", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (frozen) "Карта временно заморожена" else "Карта активна · DEMO", color = if (frozen) Color(0xFFFFB86B) else Green, fontSize = 12.sp)
            }
            Switch(checked = frozen, onCheckedChange = { onToggleFreeze() }, colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = Purple))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showDetails = !showDetails }) { Icon(if (showDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text(if (showDetails) "Скрыть реквизиты" else "Показать реквизиты") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Лимит на одну оплату", color = White, fontSize = 14.sp); Text(money(cardLimit), color = Purple, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { limitInput = cardLimit.toString(); showLimitEditor = true }) { Text("Изменить") }
        }
        Text("Управление картой", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 10.dp), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onToggleFreeze, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(if (frozen) Icons.Default.LockOpen else Icons.Default.AcUnit, null); Spacer(Modifier.width(6.dp)); Text(if (frozen) "Разморозить" else "Заморозить")
            }
            Button(onClick = onPay, enabled = !frozen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                Icon(Icons.Default.ShoppingBag, null); Spacer(Modifier.width(6.dp)); Text("Оплатить")
            }
        }
        Text("Демонстрационный макет. Карта не привязана к платёжной системе; реквизиты и платежи не настоящие.", Modifier.padding(20.dp), color = Muted, fontSize = 12.sp)
    }
    if (showLimitEditor) AlertDialog(onDismissRequest = { showLimitEditor = false }, containerColor = Panel, title = { Text("Лимит карты", color = White) }, text = { OutlinedTextField(value = limitInput, onValueChange = { limitInput = it.filter(Char::isDigit).take(9) }, label = { Text("Лимит в ₽") }, singleLine = true) }, confirmButton = { TextButton(onClick = { limitInput.toLongOrNull()?.let(onLimitChange); showLimitEditor = false }) { Text("Сохранить", color = Purple) } }, dismissButton = { TextButton(onClick = { showLimitEditor = false }) { Text("Отмена", color = Muted) } })
}

@Composable
internal fun SummaryTile(label: String, amount: Long, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Panel)) {
        Image(painterResource(R.drawable.bg_neon_history), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.18f))
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(money(amount), color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
