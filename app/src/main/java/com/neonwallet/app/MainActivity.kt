package com.neonwallet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Bg = Color(0xFF0D0B12)
private val Panel = Color(0xFF19161F)
private val Purple = Color(0xFFB49AF5)
private val Muted = Color(0xFFA39CAB)
private val White = Color(0xFFF6F2F8)
private val Green = Color(0xFF72CBA8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeonWalletApp() }
    }
}

private data class Tx(val title: String, val date: String, val amount: Long, val incoming: Boolean)
private enum class Tab { HOME, CARDS, HISTORY, PROFILE }

@Composable
fun NeonWalletApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("neon_wallet_demo", android.content.Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var balance by remember { mutableLongStateOf(prefs.getLong("balance", 128450L)) }
    var showBalance by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var txs by remember { mutableStateOf(loadTransactions(prefs)) }
    SideEffect {
        val array = JSONArray()
        txs.forEach { tx ->
            array.put(JSONObject().apply {
                put("title", tx.title); put("date", tx.date)
                put("amount", tx.amount); put("incoming", tx.incoming)
            })
        }
        prefs.edit().putLong("balance", balance).putString("transactions", array.toString()).apply()
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Purple, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.HOME -> Home(balance, showBalance, { showBalance = !showBalance },
                            { dialog = "topup" }, { dialog = "transfer" }, txs)
                        Tab.CARDS -> Cards()
                        Tab.HISTORY -> History(txs)
                        Tab.PROFILE -> Profile()
                    }
                }
                BottomBar(tab) { tab = it }
            }
            dialog?.let { kind ->
                MoneyDialog(kind, balance, onClose = { dialog = null }) { amount, recipient ->
                    if (canSubmitMoneyOperation(kind, amount, balance, recipient)) {
                        val incoming = kind == "topup"
                        balance += if (incoming) amount else -amount
                        txs = (listOf(Tx(if (incoming) "Демо-пополнение" else "Перевод · $recipient",
                            "Только что", if (incoming) amount else -amount, incoming)) + txs).take(100)
                    }
                    dialog = null
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF79659A))), CircleShape), contentAlignment = Alignment.Center) {
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
private fun Home(balance: Long, visible: Boolean, toggle: () -> Unit, topup: () -> Unit, transfer: () -> Unit, txs: List<Tx>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFBBA6EA), Color(0xFF68577E)))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF17131D), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NEON WALLET", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("ТВОИ ФИНАНСЫ ПОД КОНТРОЛЕМ", color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NotificationsNone, null, tint = White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("МОЙ СЧЁТ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3147), Color(0xFF24202B), Color(0xFF1B1821))))
                    .border(1.dp, Color(0xFFB49AF5).copy(alpha = .24f), RoundedCornerShape(30.dp))
                    .padding(23.dp)) {
                    Box(Modifier.align(Alignment.TopEnd).size(150.dp).offset(x = 55.dp, y = (-58).dp)
                        .background(Color(0xFFB49AF5).copy(alpha = .09f), CircleShape).blur(3.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Доступный баланс", color = Color(0xFFC9C0D2), fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = toggle, modifier = Modifier.size(28.dp)) {
                                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null,
                                    tint = Color(0xFFC9C0D2), modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(if (visible) money(balance) else "•••••• ₽", color = White,
                            fontSize = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                            Spacer(Modifier.width(7.dp))
                            Text("Российский рубль · RUB", color = Color(0xFFB8AFBF), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = topup, modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9A5E8)),
                                shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF211A2B))
                                Spacer(Modifier.width(7.dp))
                                Text("Пополнить", color = Color(0xFF211A2B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = transfer, modifier = Modifier.weight(1f).height(50.dp),
                                border = BorderStroke(1.dp, Color(0xFF8F829D)),
                                shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.ArrowUpward, null, tint = White)
                                Spacer(Modifier.width(7.dp))
                                Text("Перевести", color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 28.dp, bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Платежи и переводы", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Все", color = Color(0xFFB9A5E8), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                QuickAction("По номеру", Icons.Default.Person, Color(0xFFB9A5E8), transfer, Modifier.weight(1f))
                QuickAction("По QR-коду", Icons.Default.QrCodeScanner, Color(0xFF86BFA9), topup, Modifier.weight(1f))
                QuickAction("На карту", Icons.Default.CreditCard, Color(0xFFE0B68A), transfer, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 29.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Последние операции", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("История", color = Color(0xFFB9A5E8), fontSize = 12.sp)
            }
        }
        if (txs.isEmpty()) {
            item {
                Text("Здесь появятся ваши операции", Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = Muted, fontSize = 13.sp)
            }
        } else {
            items(txs.take(5)) { TransactionRow(it) }
        }
        item {
            Text("Баланс и операции отображаются в демонстрационном режиме.",
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                color = Muted.copy(alpha = .8f), fontSize = 10.sp)
        }
    }
}
@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick = action, modifier = modifier.height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (filled) Purple else Color(0xFF393142)), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, action: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).clickable { action() }.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Spacer(Modifier.height(9.dp)); Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TransactionRow(tx: Tx) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (tx.incoming) Green.copy(alpha = .13f) else Purple.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(if (tx.incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (tx.incoming) Green else Purple)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(tx.date, color = Muted, fontSize = 11.sp)
        }
        val signedAmount = when {
            tx.amount > 0 -> "+" + money(tx.amount)
            tx.amount < 0 -> "−" + money(tx.amount)
            else -> money(0)
        }
        Text(signedAmount, color = if (tx.incoming) Green else White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Cards() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 26.dp)) {
        item {
            Header()
            Text("Карты и счета", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Ваши платёжные инструменты", Modifier.padding(start = 22.dp, top = 5.dp), color = Muted, fontSize = 13.sp)
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp).height(210.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF51475F), Color(0xFF302A38), Color(0xFF211D26))))
                .border(1.dp, Color(0xFFC5B1E8).copy(alpha = .26f), RoundedCornerShape(27.dp)).padding(22.dp)) {
                Box(Modifier.align(Alignment.TopEnd).size(145.dp).offset(x = 48.dp, y = (-48).dp)
                    .background(Color(0xFFBBA6EA).copy(alpha = .12f), CircleShape))
                Column(Modifier.fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NEON", color = White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Contactless, null, tint = White.copy(alpha = .8f), modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("••••  ••••  ••••  2048", color = White, fontSize = 18.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("ВЛАДЕЛЕЦ", color = White.copy(alpha = .6f), fontSize = 9.sp, letterSpacing = 1.sp)
                            Text("NEON USER", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("VIRTUAL", color = White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 25.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Мои продукты", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("1 продукт", color = Muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp))
                .background(Panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFBBA6EA).copy(alpha = .14f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CreditCard, null, tint = Purple)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Виртуальная карта", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Макет интерфейса · не платёжный инструмент", color = Muted, fontSize = 10.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Возможности", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            listOf(
                Triple(Icons.Default.Lock, "Безопасное хранение", "Подключим после настройки защищённого сервера"),
                Triple(Icons.Default.QrCode2, "Оплата по QR-коду", "Подключение платёжного провайдера"),
                Triple(Icons.Default.AccountBalance, "Банковские переводы", "Требуется интеграция с партнёром")
            ).forEach { (icon, title, subtitle) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(15.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Purple)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(subtitle, color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun History(txs: List<Tx>) {
    Column(Modifier.fillMaxSize()) {
        Header()
        Text("История операций", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Все движения средств", Modifier.padding(start = 22.dp, top = 5.dp, bottom = 18.dp), color = Muted, fontSize = 13.sp)
        LazyColumn { items(txs) { TransactionRow(it) } }
    }
}

@Composable
private fun Profile() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Header()
            Text("Профиль", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF30283A), Panel))).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFC7B4ED), Color(0xFF78658F)))),
                    contentAlignment = Alignment.Center) {
                    Text("N", color = Color(0xFF211A2B), fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("NEON USER", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Личный кабинет", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Text("Настройки", Modifier.padding(start = 22.dp, top = 27.dp, bottom = 10.dp), color = Muted,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            listOf(
                Triple(Icons.Default.PersonOutline, "Личные данные", "Имя и контактная информация"),
                Triple(Icons.Default.Security, "Безопасность", "PIN-код и способы входа"),
                Triple(Icons.Default.NotificationsNone, "Уведомления", "Настройки оповещений"),
                Triple(Icons.Default.HelpOutline, "Помощь", "Поддержка и ответы на вопросы"),
                Triple(Icons.Default.Info, "О приложении", "Версия и юридическая информация")
            ).forEach { (icon, title, subtitle) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Panel).clickable { }.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFBBA6EA).copy(alpha = .12f)),
                        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Purple, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
        item {
            Text("NEON WALLET · PREVIEW", Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                color = Muted.copy(alpha = .65f), fontSize = 9.sp, letterSpacing = 1.3.sp)
        }
    }
}


@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF15121A)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Triple(Tab.HOME, Icons.Default.Home, "Главная"), Triple(Tab.CARDS, Icons.Default.CreditCard, "Карты"),
            Triple(Tab.HISTORY, Icons.Default.ReceiptLong, "История"), Triple(Tab.PROFILE, Icons.Default.Person, "Профиль")).forEach { (tab, icon, label) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(tab) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = if (selected == tab) Purple else Muted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(3.dp)); Text(label, color = if (selected == tab) Purple else Muted, fontSize = 10.sp, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MoneyDialog(kind: String, balance: Long, onClose: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    val transfer = kind == "transfer"
    val parsedAmount = amount.toLongOrNull() ?: 0L
    val canSubmit = canSubmitMoneyOperation(kind, parsedAmount, balance, who)
    AlertDialog(onDismissRequest = onClose, containerColor = Panel,
        title = { Text(if (transfer) "Новый перевод" else "Пополнение", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = who, onValueChange = { who = it }, label = { Text(if (transfer) "Кому (демо)" else "Источник (демо)") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedLabelColor = Purple, unfocusedLabelColor = Muted))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(9) }, label = { Text("Сумма в ₽") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedLabelColor = Purple, unfocusedLabelColor = Muted))
                if (transfer && who.isNotBlank() && parsedAmount > balance) {
                    Text("Недостаточно средств на демо-балансе.", color = Color(0xFFFF8A8A), fontSize = 11.sp)
                } else if (transfer && who.isBlank()) {
                    Text("Укажи получателя перевода.", color = Muted, fontSize = 11.sp)
                } else if (amount.isNotBlank() && parsedAmount <= 0L) {
                    Text("Введи сумму больше нуля.", color = Color(0xFFFF8A8A), fontSize = 11.sp)
                }
                Text("Демо-режим: реальные деньги не перемещаются.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(enabled = canSubmit, onClick = { onConfirm(parsedAmount, who.trim()) }) { Text(if (transfer) "Отправить" else "Добавить", color = Purple) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена", color = Muted) } }
    )
}

private fun money(value: Long): String = NumberFormat.getNumberInstance(Locale("ru", "RU")).format(kotlin.math.abs(value)) + " ₽"

private fun loadTransactions(prefs: android.content.SharedPreferences): List<Tx> {
    val stored = prefs.getString("transactions", null)
    if (stored.isNullOrBlank()) return listOf(
        Tx("Пополнение", "Сегодня · 10:42", 25000, true),
        Tx("Coffee House", "Сегодня · 09:18", -680, false),
        Tx("Перевод от Alex", "Вчера · 18:35", 12000, true),
        Tx("Подписка", "Вчера · 12:00", -499, false)
    )
    return try {
        val array = JSONArray(stored)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Tx(item.getString("title"), item.getString("date"), item.getLong("amount"), item.getBoolean("incoming"))
        }
    } catch (_: Exception) { emptyList() }
}
