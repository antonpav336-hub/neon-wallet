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
internal fun Payments(balance: Long, cardFrozen: Boolean, cardLimit: Long, onTransfer: () -> Unit, onTopup: () -> Unit, onWithdraw: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Text("Платежи и переводы", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Все основные операции — в одном месте", Modifier.padding(start = 22.dp, top = 5.dp), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(17.dp)) {
            Text("Доступно на демо-счёте", color = Muted, fontSize = 11.sp)
            Text(money(balance), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Счёт списания · RUB", color = Muted, fontSize = 10.sp)
        }
        Text("Операции", Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        PaymentAction("Перевести", "По телефону, ID или имени получателя", Icons.Default.Send, onTransfer)
        PaymentAction("Пополнить счёт", "Демо-зачисление на баланс", Icons.Default.AddCard, onTopup)
        PaymentAction("Вывести средства", "Демо-операция вывода", Icons.Default.SouthWest, onWithdraw)
        Text("Условия перед подтверждением", Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
            Text("Лимит оплаты картой: " + money(cardLimit), color = White, fontSize = 12.sp)
            Divider(color = Color(0xFF30263D), modifier = Modifier.padding(vertical = 12.dp))
            Text(if (cardFrozen) "Карта заморожена" else "Карта активна", color = if (cardFrozen) Color(0xFFFFB86B) else Green, fontSize = 12.sp)
        }
        Text("Комиссии и межбанковские переводы не подключены. Все операции демонстрационные.", Modifier.padding(20.dp), color = Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun PaymentAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(17.dp)).background(Panel).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Purple.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Purple) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 10.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}
