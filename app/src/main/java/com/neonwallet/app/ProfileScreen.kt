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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun Profile(email: String, fullName: String, accountId: String, onSignOut: () -> Unit) {
    var openedSection by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Text("Профиль", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B3448), Panel)))
            .border(1.dp, Purple.copy(alpha = .22f), RoundedCornerShape(23.dp))) {
            Image(painterResource(R.drawable.bg_neon_profile), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.22f))
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFD0C0EE), Color(0xFF82709D)))),
                    contentAlignment = Alignment.Center) {
                    Text(displayInitials(fullName), color = Color(0xFF211A2B), fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(shortDisplayName(fullName), color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(email.ifBlank { "Аккаунт NEON" }, color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("NEON ID: " + accountId.ifBlank { "—" }, color = Purple, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(28.dp))
        val sections = listOf(
            Triple("Личные данные", Icons.Default.Person, "personal"),
            Triple("Безопасность", Icons.Default.Shield, "security"),
            Triple("Уведомления", Icons.Default.Notifications, "notifications"),
            Triple("О приложении", Icons.Default.Info, "about")
        )
        sections.forEach { (label, icon, key) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(15.dp)).background(Panel)
                    .clickable { openedSection = key }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Purple)
                Spacer(Modifier.width(13.dp))
                Text(label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Text("Защита аккаунта", Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A8A)),
            border = BorderStroke(1.dp, Color(0xFFFF8A8A).copy(alpha = .5f))) {
            Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Выйти из аккаунта")
        }
        Spacer(Modifier.height(24.dp))
    }

    openedSection?.let { section ->
        val title = when (section) {
            "personal" -> "Личные данные"
            "security" -> "Безопасность"
            "notifications" -> "Уведомления"
            else -> "О приложении"
        }
        val message = when (section) {
            "personal" -> "${fullName.ifBlank { "Имя не указано" }}\n${email.ifBlank { "Не указано" }}\n\nДанные аккаунта хранятся на этом устройстве."
            "security" -> "Для входа используется PIN-код. Он хранится в защищённом виде на этом устройстве. Биометрический вход можно подключить отдельно."
            "notifications" -> "Push-уведомления и банковские оповещения пока не подключены. Историю операций можно посмотреть во вкладке «История»."
            else -> "NEON WALLET\nВерсия 1.0.0\n\nПриложение работает автономно. Подключение к банковской инфраструктуре и реальные денежные переводы пока недоступны."
        }
        AlertDialog(
            onDismissRequest = { openedSection = null },
            containerColor = Panel,
            title = { Text(title, color = White, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = Muted, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { openedSection = null }) {
                    Text("Понятно", color = Purple, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = Panel,
            title = { Text("Выйти из аккаунта?", color = White, fontWeight = FontWeight.Bold) },
            text = { Text("Текущая сессия на этом устройстве будет завершена. Сохранённая история операций останется на устройстве.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Выйти", color = Color(0xFFFF8A8A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Отмена", color = Muted) }
            }
        )
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
