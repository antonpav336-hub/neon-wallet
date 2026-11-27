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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header() }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ТВОИ ФИНАНСЫ", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF30263F), Color(0xFF211C2B), Color(0xFF17141D))))
                    .border(1.dp, Color(0xFFB49AF5).copy(alpha = .22f), RoundedCornerShape(28.dp)).padding(22.dp)) {
                    Box(Modifier.align(Alignment.TopEnd).size(130.dp).offset(x = 42.dp, y = (-45).dp).background(Purple.copy(alpha = .12f), CircleShape).blur(2.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Общий баланс", color = Color(0xFFC7B9DB), fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = toggle, modifier = Modifier.size(28.dp)) {
                                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = White, modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(if (visible) money(balance) else "•••••• ₽", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Демо-счёт · Российский рубль", color = Color(0xFFC7B9DB), fontSize = 12.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton("Пополнить", Icons.Default.Add, topup, true, Modifier.weight(1f))
                            ActionButton("Перевести", Icons.Default.ArrowUpward, transfer, false, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Быстрые действия", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Перевод", Icons.Default.Send, Purple, transfer, Modifier.weight(1f))
                QuickAction("Пополнить", Icons.Default.AddCard, Green, topup, Modifier.weight(1f))
            }
        }
        item { Text("Последние операции", Modifier.padding(horizontal = 22.dp, vertical = 20.dp), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        items(txs.take(5)) { TransactionRow(it) }
        item { Text("ДЕМО-БАЛАНС · НЕ РЕАЛЬНЫЕ ДЕНЬГИ", Modifier.fillMaxWidth().padding(20.dp), color = Muted, fontSize = 10.sp) }
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
    Column(Modifier.fillMaxSize()) {
        Header()
        Text("Мои карты", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Управляй своими картами", Modifier.padding(start = 22.dp, top = 5.dp), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(205.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF74618F), Color(0xFF40334F), Color(0xFF211B29))))
            .border(1.dp, Color(0xFFB58BFF).copy(alpha = .4f), RoundedCornerShape(26.dp)).padding(22.dp)) {
            Column(Modifier.fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NEON WALLET", color = White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Contactless, null, tint = White)
                }
                Spacer(Modifier.weight(1f))
                Text("••••  ••••  ••••  2048", color = White, fontSize = 19.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(15.dp))
                Row {
                    Column { Text("ВЛАДЕЛЕЦ", color = White.copy(alpha = .65f), fontSize = 9.sp); Text("NEON USER", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f)); Text("VISA", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Виртуальная карта · DEMO", Modifier.padding(horizontal = 20.dp), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Демонстрационный макет. Карта не привязана к платёжной системе.", Modifier.padding(20.dp), color = Muted, fontSize = 12.sp)
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
    Column(Modifier.fillMaxSize()) {
        Header()
        Text("Профиль", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(82.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Color(0xFF79659A))), CircleShape), contentAlignment = Alignment.Center) {
            Text("N", color = White, fontSize = 35.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text("NEON USER", Modifier.align(Alignment.CenterHorizontally), color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Демо-аккаунт", Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        listOf("Личные данные", "Безопасность", "Уведомления", "О приложении").forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(15.dp)).background(Panel).clickable { }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(listOf(Icons.Default.Person, Icons.Default.Shield, Icons.Default.Notifications, Icons.Default.Info)[i], null, tint = Purple)
                Spacer(Modifier.width(13.dp)); Text(label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
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
