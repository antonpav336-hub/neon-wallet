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
internal fun Profile(email: String, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Text("Профиль", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(82.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Color(0xFF5426A6))), CircleShape), contentAlignment = Alignment.Center) {
            Text("N", color = White, fontSize = 35.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text("NEON USER", Modifier.align(Alignment.CenterHorizontally), color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(email.ifBlank { "Локальный демо-аккаунт" }, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        listOf("Личные данные", "Безопасность", "Уведомления", "О приложении").forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(15.dp)).background(Panel).clickable { }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(listOf(Icons.Default.Person, Icons.Default.Shield, Icons.Default.Notifications, Icons.Default.Info)[i], null, tint = Purple)
                Spacer(Modifier.width(13.dp)); Text(label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Text("PIN и биометрическая защита", Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Пока не подключены. Текущий вход — локальный демо-аккаунт на устройстве.", Modifier.padding(horizontal = 22.dp), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A8A)),
            border = BorderStroke(1.dp, Color(0xFFFF8A8A).copy(alpha = .5f))) {
            Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Выйти из аккаунта")
        }
    }
}

@Composable
internal fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF100D18)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Triple(Tab.HOME, Icons.Default.Home, "Главная"), Triple(Tab.PAYMENTS, Icons.Default.SwapHoriz, "Платежи"),
            Triple(Tab.CARDS, Icons.Default.CreditCard, "Карты"), Triple(Tab.HISTORY, Icons.Default.ReceiptLong, "История"), Triple(Tab.PROFILE, Icons.Default.Person, "Профиль")).forEach { (tab, icon, label) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(tab) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = if (selected == tab) Purple else Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp)); Text(label, color = if (selected == tab) Purple else Muted, fontSize = 9.sp, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
