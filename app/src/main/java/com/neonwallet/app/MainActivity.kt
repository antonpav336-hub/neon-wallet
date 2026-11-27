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

private val Bg = Color(0xFF090711)
private val Panel = Color(0xFF171323)
private val Purple = Color(0xFF9B5CFF)
private val Muted = Color(0xFF9B94AA)
private val White = Color(0xFFF7F3FF)
private val Green = Color(0xFF48D7A0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeonWalletApp() }
    }
}

private data class Tx(val title: String, val date: String, val amount: Long, val incoming: Boolean, val recipient: String = "", val status: String = "Успешно", val timestamp: Long = System.currentTimeMillis(), val kind: String = if (incoming) "topup" else "transfer")
private enum class Tab { HOME, CARDS, HISTORY, PROFILE }

@Composable
fun NeonWalletApp() {
    val context = LocalContext.current
    val authPrefs = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE) }
    var signedIn by remember { mutableStateOf(authPrefs.getBoolean("signed_in", false)) }
    if (!signedIn) {
        AuthScreen(authPrefs) {
            authPrefs.edit().putBoolean("signed_in", true).apply()
            signedIn = true
        }
    } else {
        WalletMain {
            authPrefs.edit().putBoolean("signed_in", false).apply()
            signedIn = false
        }
    }
}

@Composable
private fun WalletMain(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("neon_wallet_demo", android.content.Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var balance by remember { mutableLongStateOf(prefs.getLong("balance", 128450L)) }
    var showBalance by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var txs by remember { mutableStateOf(loadTransactions(prefs)) }
    var cardFrozen by remember { mutableStateOf(prefs.getBoolean("card_frozen", false)) }
    var cardLimit by remember { mutableLongStateOf(prefs.getLong("card_limit", 50000L)) }
    var isSubmitting by remember { mutableStateOf(false) }
    val accountEmail = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE).getString("current_email", "") ?: "" }
    SideEffect {
        val array = JSONArray()
        txs.forEach { tx ->
            array.put(JSONObject().apply {
                put("title", tx.title); put("date", tx.date)
                put("amount", tx.amount); put("incoming", tx.incoming); put("recipient", tx.recipient); put("status", tx.status); put("timestamp", tx.timestamp); put("kind", tx.kind)
            })
        }
        prefs.edit().putLong("balance", balance).putString("transactions", array.toString()).apply()
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Purple, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(targetState = tab, transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(150))
                    }, label = "neon_wallet_tabs") { screen ->
                        when (screen) {
                            Tab.HOME -> Home(balance, showBalance, { showBalance = !showBalance },
                                { dialog = "topup" }, { dialog = "transfer" }, { dialog = "withdraw" }, txs)
                            Tab.CARDS -> Cards(cardFrozen, cardLimit, {
                                cardFrozen = !cardFrozen
                                prefs.edit().putBoolean("card_frozen", cardFrozen).apply()
                            }, { newLimit -> cardLimit = newLimit.coerceAtLeast(0L); prefs.edit().putLong("card_limit", cardLimit).apply() }) { if (canUseCard(cardFrozen)) dialog = "card_payment" }
                            Tab.HISTORY -> History(txs)
                            Tab.PROFILE -> Profile(accountEmail, onSignOut)
                        }
                    }
                }
                BottomBar(tab) { tab = it }
            }
            dialog?.let { kind ->
                MoneyDialog(kind, balance, cardFrozen, cardLimit, onClose = { if (!isSubmitting) dialog = null }) { amount, recipient ->
                    if (canStartOperation(isSubmitting) && canSubmitMoneyOperation(kind, amount, balance, recipient, cardFrozen, cardLimit)) {
                        isSubmitting = true
                        val incoming = kind == "topup"
                        val title = when (kind) {
                            "topup" -> "Демо-пополнение"
                            "withdraw" -> "Демо-вывод"
                            "card_payment" -> "Оплата картой · $recipient"
                            else -> "Перевод · $recipient"
                        }
                        balance += if (incoming) amount else -amount
                        val now = System.currentTimeMillis()
                        val date = java.text.SimpleDateFormat("dd MMM, HH:mm", Locale("ru")).format(java.util.Date(now))
                        txs = (listOf(Tx(title, date, if (incoming) amount else -amount, incoming, recipient.trim(), "Успешно", now, kind)) + txs).take(100)
                        isSubmitting = false
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
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
private fun Home(balance: Long, visible: Boolean, toggle: () -> Unit, topup: () -> Unit, transfer: () -> Unit, withdraw: () -> Unit, txs: List<Tx>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header() }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ТВОИ ФИНАНСЫ", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3C1C68), Color(0xFF21143D), Color(0xFF151020))))
                    .border(1.dp, Color(0xFF8050C5).copy(alpha = .45f), RoundedCornerShape(28.dp)).padding(22.dp)) {
                    Box(Modifier.align(Alignment.TopEnd).size(130.dp).offset(x = 42.dp, y = (-45).dp).background(Purple.copy(alpha = .12f), CircleShape).blur(2.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Общий баланс", color = Color(0xFFC7B9DB), fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = toggle, modifier = Modifier.size(28.dp)) {
                                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = White, modifier = Modifier.size(17.dp))
                            }
                        }
                        AnimatedContent(targetState = if (visible) money(balance) else "•••••• ₽", transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "balance_visibility") { value ->
                            Text(value, color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Демо-счёт · Российский рубль", color = Color(0xFFC7B9DB), fontSize = 12.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Пополнить", Icons.Default.Add, topup, true, Modifier.weight(1f))
                            ActionButton("Перевести", Icons.Default.Send, transfer, false, Modifier.weight(1f))
                            ActionButton("Вывести", Icons.Default.SouthWest, withdraw, false, Modifier.weight(1f))
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
                QuickAction("Вывести", Icons.Default.SouthWest, Color(0xFFFFB86B), withdraw, Modifier.weight(1f))
            }
        }
        item { Text("Последние операции", Modifier.padding(horizontal = 22.dp, vertical = 20.dp), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        items(txs.take(5)) { TransactionRow(it) }
        item { Text("ДЕМО-БАЛАНС · НЕ РЕАЛЬНЫЕ ДЕНЬГИ", Modifier.fillMaxWidth().padding(20.dp), color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick = action, modifier = modifier.height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (filled) Purple else Color(0xFF33244C)), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).animateContentSize().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun Cards(frozen: Boolean, cardLimit: Long, onToggleFreeze: () -> Unit, onLimitChange: (Long) -> Unit, onPay: () -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    var showLimitEditor by remember { mutableStateOf(false) }
    var limitInput by remember(cardLimit) { mutableStateOf(cardLimit.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Text("Мои карты", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Управляй своими картами", Modifier.padding(start = 22.dp, top = 5.dp), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(205.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7D43D8), Color(0xFF32165C), Color(0xFF160E26))))
            .border(1.dp, Color(0xFFB58BFF).copy(alpha = .4f), RoundedCornerShape(26.dp)).padding(22.dp)) {
            Column(Modifier.fillMaxHeight()) {
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
            TextButton(onClick = { showDetails = !showDetails }) { Icon(if (showDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text(if (showDetails) "Скрыть демо-реквизиты" else "Показать демо-реквизиты") }
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
    if (showLimitEditor) AlertDialog(onDismissRequest = { showLimitEditor = false }, containerColor = Panel, title = { Text("Лимит демо-карты", color = White) }, text = { OutlinedTextField(value = limitInput, onValueChange = { limitInput = it.filter(Char::isDigit).take(9) }, label = { Text("Лимит в ₽") }, singleLine = true) }, confirmButton = { TextButton(onClick = { limitInput.toLongOrNull()?.let(onLimitChange); showLimitEditor = false }) { Text("Сохранить", color = Purple) } }, dismissButton = { TextButton(onClick = { showLimitEditor = false }) { Text("Отмена", color = Muted) } })
}

@Composable
private fun SummaryTile(label: String, amount: Long, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(money(amount), color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun History(txs: List<Tx>) {
    var query by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf("all") }
    val filtered = txs.filter { (selectedKind == "all" || it.kind == selectedKind) && (it.title.contains(query, true) || it.recipient.contains(query, true) || it.status.contains(query, true)) }
    val income = txs.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = txs.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
    Column(Modifier.fillMaxSize()) {
        Header()
        Text("История операций", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Все движения средств", Modifier.padding(start = 22.dp, top = 5.dp, bottom = 14.dp), color = Muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Доходы", income, Green, Modifier.weight(1f))
            SummaryTile("Расходы", expense, Color(0xFFFF9292), Modifier.weight(1f))
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), singleLine = true, placeholder = { Text("Поиск по операции или получателю", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Все", "topup" to "Пополнения", "transfer" to "Переводы", "withdraw" to "Выводы", "card_payment" to "Оплаты").forEach { (key, label) ->
                FilterChip(selected = selectedKind == key, onClick = { selectedKind = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple.copy(alpha = .25f), selectedLabelColor = White, labelColor = Muted))
            }
        }
        LazyColumn { items(filtered) { TransactionRow(it) } }
    }
}

@Composable
private fun Profile(email: String, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
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
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A8A)),
            border = BorderStroke(1.dp, Color(0xFFFF8A8A).copy(alpha = .5f))) {
            Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Выйти из аккаунта")
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF100D18)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
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
private fun MoneyDialog(
    kind: String,
    balance: Long,
    cardFrozen: Boolean,
    cardLimit: Long,
    onClose: () -> Unit,
    onConfirm: (Long, String) -> Boolean
) {
    var amount by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("editing") }
    var failure by remember { mutableStateOf<String?>(null) }
    val parsedAmount = amount.toLongOrNull() ?: 0L
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favoritePrefs = remember(context) { context.getSharedPreferences("neon_transfer_favorites", android.content.Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(favoritePrefs.getStringSet("recipients", emptySet())?.toList()?.sorted() ?: emptyList()) }
    val title = when (kind) { "transfer" -> "Новый перевод"; "withdraw" -> "Вывод средств"; "card_payment" -> "Оплата картой"; else -> "Пополнение" }
    val action = when (kind) { "transfer" -> "Перевести"; "withdraw" -> "Вывести"; "card_payment" -> "Оплатить"; else -> "Пополнить" }
    AlertDialog(
        onDismissRequest = { if (stage != "processing") onClose() },
        containerColor = Panel,
        title = { Text(when (stage) { "processing" -> "Обрабатываем операцию"; "success" -> "Операция выполнена"; "error" -> "Операция отклонена"; else -> title }, color = White, fontWeight = FontWeight.Bold) },
        text = {
            AnimatedContent(targetState = stage, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "money_operation_stage") { current ->
                when (current) {
                    "processing" -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val pulse by animateFloatAsState(1.08f, tween(420), label = "processing_pulse")
                        Box(Modifier.size(92.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Purple, strokeWidth = 4.dp, modifier = Modifier.size(76.dp))
                            Icon(Icons.Default.Bolt, null, tint = White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Проверяем данные и обновляем демо-баланс…", color = Muted, fontSize = 13.sp)
                    }
                    "success" -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OperationStatusMark(success = true)
                        Spacer(Modifier.height(14.dp))
                        Text(when (kind) { "topup" -> "Пополнение выполнено"; "withdraw" -> "Вывод оформлен"; "card_payment" -> "Оплата выполнена"; else -> "Перевод отправлен" }, color = Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(money(parsedAmount), color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (who.isBlank()) "Демо-пополнение" else who, color = Muted, fontSize = 13.sp)
                    }
                    "error" -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OperationStatusMark(success = false)
                        Spacer(Modifier.height(14.dp))
                        Text("Не удалось выполнить", color = Color(0xFFFF858D), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(failure ?: "Операция отклонена.", color = Muted, fontSize = 13.sp)
                    }
                    else -> Column {
                        OutlinedTextField(value = who, onValueChange = { who = it; failure = null }, label = { Text(when (kind) { "transfer" -> "Кому (демо)"; "withdraw" -> "Куда вывести (демо)"; "card_payment" -> "Магазин / назначение"; else -> "Источник (демо)" }) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedLabelColor = Purple, unfocusedLabelColor = Muted))
                        if (kind == "transfer") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (listOf("Alex", "Мама", "Самому себе") + favorites).distinct().forEach { preset ->
                                    TextButton(onClick = { who = preset; failure = null }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(preset, color = Purple, fontSize = 11.sp) }
                                }
                            }
                            if (who.isNotBlank() && who !in favorites) {
                                TextButton(onClick = {
                                    favorites = (favorites + who.trim()).distinct().take(12)
                                    favoritePrefs.edit().putStringSet("recipients", favorites.toSet()).apply()
                                }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Icon(Icons.Default.StarBorder, null, tint = Purple, modifier = Modifier.size(15.dp))
                                    Text("Добавить получателя в избранное", color = Purple, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(9); failure = null }, label = { Text("Сумма в ₽") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedLabelColor = Purple, unfocusedLabelColor = Muted))
                        if (failure != null) Text(failure!!, color = Color(0xFFFF858D), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        Text("Демо-режим: реальные деньги не перемещаются.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                "editing" -> TextButton(onClick = {
                    val reason = operationFailureReason(kind, parsedAmount, balance, who, cardFrozen, cardLimit)
                    if (reason != null) { failure = reason; stage = "error" }
                    else {
                        stage = "processing"
                        scope.launch {
                            delay(820)
                            val accepted = onConfirm(parsedAmount, who.trim())
                            if (accepted) stage = "success"
                            else {
                                failure = operationFailureReason(kind, parsedAmount, balance, who, cardFrozen, cardLimit) ?: "Операция уже обрабатывается или была отклонена."
                                stage = "error"
                            }
                        }
                    }
                }) { Text(action, color = Purple) }
                "success", "error" -> TextButton(onClick = onClose) { Text("Готово", color = Purple, fontWeight = FontWeight.Bold) }
                else -> {}
            }
        },
        dismissButton = { if (stage == "editing" || stage == "error") TextButton(onClick = { if (stage == "error") { stage = "editing"; failure = null } else onClose() }) { Text(if (stage == "error") "Изменить" else "Отмена", color = Muted) } }
    )
}

@Composable
private fun OperationStatusMark(success: Boolean) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(success) { started = true }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(520), label = "status_mark_progress")
    val tint by animateColorAsState(if (success) Green else Color(0xFFFF6675), tween(220), label = "status_mark_color")
    val shake by animateFloatAsState(if (started && !success) 1f else 0f, tween(360), label = "status_mark_shake")
    Canvas(Modifier.size(88.dp).graphicsLayer {
        val wobble = if (success) 0f else kotlin.math.sin(shake * Math.PI * 8).toFloat() * (1f - shake) * 9f
        translationX = wobble
        val bounce = if (success) kotlin.math.sin(progress * Math.PI).toFloat() * .12f else 0f
        scaleX = (if (success) .86f + .14f * progress else 1f) + bounce
        scaleY = (if (success) .86f + .14f * progress else 1f) + bounce
    }) {
        val stroke = 5.dp.toPx()
        drawCircle(tint.copy(alpha = .13f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(tint, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        if (success) {
            val firstEnd = .25f + .18f * progress
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .52f), androidx.compose.ui.geometry.Offset(size.width * firstEnd, size.height * (.52f + .16f * progress)), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val secondProgress = ((progress - .35f) / .65f).coerceIn(0f, 1f)
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .43f, size.height * .68f), androidx.compose.ui.geometry.Offset(size.width * (.43f + .33f * secondProgress), size.height * (.68f - .34f * secondProgress)), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        } else {
            drawLine(tint.copy(alpha = progress), androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .34f), androidx.compose.ui.geometry.Offset(size.width * (.34f + .32f * progress), size.height * (.34f + .32f * progress)), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint.copy(alpha = progress), androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .34f), androidx.compose.ui.geometry.Offset(size.width * (.66f - .32f * progress), size.height * (.34f + .32f * progress)), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
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
            Tx(item.getString("title"), item.getString("date"), item.getLong("amount"), item.getBoolean("incoming"), item.optString("recipient", ""), item.optString("status", "Успешно"), item.optLong("timestamp", 0L), item.optString("kind", if (item.getBoolean("incoming")) "topup" else "transfer"))
        }
    } catch (_: Exception) { emptyList() }
}

@Composable
private fun AuthScreen(prefs: android.content.SharedPreferences, onSuccess: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF160D27), Bg, Color(0xFF090711))))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 65.dp, y = (-65).dp).size(240.dp).background(Purple.copy(alpha = .14f), CircleShape).blur(65.dp))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = 80.dp).size(220.dp).background(Color(0xFF5426A6).copy(alpha = .16f), CircleShape).blur(60.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).shadow(24.dp, RoundedCornerShape(25.dp), ambientColor = Purple, spotColor = Purple)
                .clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF5426A6)))),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = White, modifier = Modifier.size(43.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("NEON WALLET", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("ТВОИ ФИНАНСЫ. ТВОИ ПРАВИЛА.", color = Color(0xFFB9A5D8), fontSize = 10.sp, letterSpacing = 1.7.sp)
            Spacer(Modifier.height(38.dp))
            Text(if (register) "Создать аккаунт" else "С возвращением", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(if (register) "Зарегистрируй свой NEON ID" else "Войди, чтобы продолжить", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp, bottom = 24.dp))
            AuthField(email, { email = it; error = "" }, "Электронная почта", Icons.Default.Email)
            Spacer(Modifier.height(12.dp))
            AuthField(password, { password = it; error = "" }, "Пароль", Icons.Default.Lock, true)
            if (register) {
                Spacer(Modifier.height(12.dp))
                AuthField(confirm, { confirm = it; error = "" }, "Повтори пароль", Icons.Default.Lock, true)
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF9292), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                keyboard?.hide()
                val normalized = email.trim().lowercase()
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches() -> error = "Проверь адрес электронной почты."
                    password.length < 8 -> error = "Пароль должен содержать минимум 8 символов."
                    register && password != confirm -> error = "Пароли не совпадают."
                    else -> {
                        busy = true
                        val key = "user_" + normalized
                        val existing = prefs.getString(key, null)
                        if (register && existing != null) {
                            error = "Аккаунт с такой почтой уже существует."
                            busy = false
                        } else if (!register && existing == null) {
                            error = "Аккаунт не найден. Сначала зарегистрируйся."
                            busy = false
                        } else {
                            val saved = if (register) {
                                val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                                val hash = passwordHash(password, salt)
                                android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP) + ":" +
                                    android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
                            } else existing!!
                            if (!register && !verifyPassword(password, saved)) {
                                error = "Неверная почта или пароль."
                                busy = false
                            } else {
                                prefs.edit().putString(key, saved).putString("current_email", normalized).apply()
                                busy = false
                                onSuccess()
                            }
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !busy,
                shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                if (busy) CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (register) "Создать аккаунт" else "Войти", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (register) "Уже есть аккаунт?" else "Впервые в NEON?", color = Muted, fontSize = 13.sp)
                TextButton(onClick = { register = !register; error = ""; confirm = "" }) {
                    Text(if (register) "Войти" else "Создать аккаунт", color = Purple, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Локальный прототип аккаунта. Данные не синхронизируются между устройствами; серверная авторизация ещё не подключена.",
                color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, label: String,
                      icon: androidx.compose.ui.graphics.vector.ImageVector, secret: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        singleLine = true, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = Purple) },
        visualTransformation = if (secret && !visible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (secret) ({ IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Muted) } }) else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White,
            focusedBorderColor = Purple, unfocusedBorderColor = Color(0xFF49375E),
            focusedLabelColor = Purple, unfocusedLabelColor = Muted, cursorColor = Purple))
}

private fun passwordHash(password: String, salt: ByteArray): ByteArray {
    val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120000, 256)
    return try { javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
    finally { spec.clearPassword() }
}

private fun verifyPassword(password: String, saved: String): Boolean {
    return try {
        val parts = saved.split(":")
        if (parts.size != 2) return false
        val salt = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val expected = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        java.security.MessageDigest.isEqual(passwordHash(password, salt), expected)
    } catch (_: Exception) { false }
}
