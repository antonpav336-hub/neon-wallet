package com.neonwallet.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport android.content.pm.PackageManager\nimport androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalContext\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver\nimport androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeonWalletApp() }
    }
}

@Composable
fun NeonWalletApp() {
    val context = LocalContext.current
    val authPrefs = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var signedIn by remember { mutableStateOf(authPrefs.getBoolean("signed_in", false)) }
    var pinUnlocked by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, signedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && signedIn) pinUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!signedIn) {
        AuthScreen(authPrefs) {
            authPrefs.edit().putBoolean("signed_in", true).apply()
            signedIn = true
            pinUnlocked = false
        }
    } else if (!pinUnlocked) {
        PinGateScreen(authPrefs, setup = !authPrefs.contains("pin_hash")) { pinUnlocked = true }
    } else {
        WalletMain {
            authPrefs.edit().putBoolean("signed_in", false).apply()
            signedIn = false
            pinUnlocked = false
        }
    }
}

@Composable
private fun WalletMain(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val authPrefs = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE) }
    val accountEmail = authPrefs.getString("current_email", "") ?: ""
    val accountKey = remember(accountEmail) { android.util.Base64.encodeToString(accountEmail.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING) }
    val prefs = remember(context, accountKey) { context.getSharedPreferences("neon_wallet_demo_$accountKey", android.content.Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var balance by remember(accountKey) { mutableLongStateOf(prefs.getLong("balance", 128450L)) }
    var showBalance by remember(accountKey) { mutableStateOf(prefs.getBoolean("show_balance", true)) }
    var dialog by remember { mutableStateOf<String?>(null) }\n    var qrRecipient by remember { mutableStateOf("") }\n    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n        dialog = if (granted) "qr" else "camera_permission"\n    }
    var txs by remember(accountKey) { mutableStateOf(loadTransactions(prefs)) }
    var cardFrozen by remember(accountKey) { mutableStateOf(prefs.getBoolean("card_frozen", false)) }
    var cardLimit by remember(accountKey) { mutableLongStateOf(prefs.getLong("card_limit", 50000L)) }
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedReceipt by remember { mutableStateOf<Tx?>(null) }
    SideEffect {
        val array = JSONArray()
        txs.forEach { tx ->
            array.put(JSONObject().apply {
                put("title", tx.title); put("date", tx.date); put("amount", tx.amount)
                put("incoming", tx.incoming); put("recipient", tx.recipient); put("status", tx.status)
                put("timestamp", tx.timestamp); put("kind", tx.kind); put("bank", tx.bank); put("operationId", tx.operationId)
            })
        }
        prefs.edit().putLong("balance", balance).putString("transactions", array.toString())
            .putBoolean("show_balance", showBalance).putBoolean("card_frozen", cardFrozen)
            .putLong("card_limit", cardLimit).apply()
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Purple, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(targetState = tab, transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(150))
                    }, label = "neon_wallet_tabs") { screen ->
                        when (screen) {
                            Tab.HOME -> Home(balance, showBalance, { showBalance = !showBalance }, { dialog = "topup" },
                                { dialog = "transfer" }, { dialog = "withdraw" }, txs, { tab = Tab.CARDS }, { tab = Tab.HISTORY },
                                { selectedReceipt = it })
                            Tab.PAYMENTS -> Payments(balance, cardFrozen, cardLimit, { dialog = "transfer" }, { dialog = "topup" }, { dialog = "withdraw" }) {\n                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) dialog = "qr"\n                                else cameraPermission.launch(android.Manifest.permission.CAMERA)\n                            }
                            Tab.CARDS -> Cards(cardFrozen, cardLimit, {
                                cardFrozen = !cardFrozen
                            }, { cardLimit = it.coerceAtLeast(0L) }) { if (canUseCard(cardFrozen)) dialog = "card_payment" }
                            Tab.HISTORY -> History(txs) { selectedReceipt = it }
                            Tab.PROFILE -> Profile(accountEmail, onSignOut)
                        }
                    }
                }
                BottomBar(tab) { tab = it }
            }
            dialog?.let { kind ->
                when (kind) {
                    "qr" -> QRScannerDialog(
                        onClose = { dialog = null },
                        onScanned = { value -> qrRecipient = value; dialog = "transfer" }
                    )
                    "camera_permission" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        containerColor = Panel,
                        title = { Text("Нужен доступ к камере", color = White) },
                        text = { Text("Разреши доступ к камере Android, чтобы сканировать QR-коды.", color = Muted) },
                        confirmButton = { TextButton(onClick = { dialog = null; cameraPermission.launch(android.Manifest.permission.CAMERA) }) { Text("Разрешить", color = Purple) } },
                        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Позже", color = Muted) } }
                    )
                    else -> MoneyDialog(kind, balance, cardFrozen, cardLimit, onClose = { if (!isSubmitting) dialog = null },
                    onBegin = { if (canStartOperation(isSubmitting)) { isSubmitting = true; true } else false },
                    onFinish = { isSubmitting = false }, initialRecipient = qrRecipient) { amount, recipient, bank ->
                    val accepted = canSubmitMoneyOperation(kind, amount, balance, recipient, cardFrozen, cardLimit, bank = bank)
                    if (accepted) {
                        val incoming = kind == "topup"
                        val title = when (kind) {
                            "topup" -> "Пополнение"
                            "withdraw" -> "Вывод"
                            "card_payment" -> "Оплата картой · $recipient"
                            else -> "Перевод · $recipient"
                        }
                        balance = if (incoming) balance + amount else balance - amount
                        val now = System.currentTimeMillis()
                        val date = java.text.SimpleDateFormat("dd MMM · HH:mm", Locale("ru")).format(java.util.Date(now))
                        val operationId = "NW$now"
                        txs = (listOf(Tx(title, date, if (incoming) amount else -amount, incoming, recipient.trim(),
                            "Успешно", now, kind, bank, operationId)) + txs).take(100)
                    }
                    accepted
                }
                }
            }
            selectedReceipt?.let { tx ->
                AlertDialog(onDismissRequest = { selectedReceipt = null }, containerColor = Panel,
                    title = { Text("Чек операции", color = White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tx.title, color = White, fontWeight = FontWeight.SemiBold)
                            Text((if (tx.amount >= 0) "+" else "−") + money(tx.amount), color = if (tx.amount >= 0) Green else White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Получатель: " + tx.recipient.ifBlank { "—" }, color = Muted)
                            Text("Банк: " + tx.bank, color = Muted)
                            Text("Дата: " + tx.date, color = Muted)
                            Text("Статус: " + tx.status, color = Green)
                            Text("ID: " + tx.operationId.ifBlank { "—" }, color = Muted)
                            Text("ДЕМО · реальные деньги не перемещаются", color = Muted, fontSize = 10.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { selectedReceipt = null }) { Text("Готово", color = Purple) } },
                    dismissButton = { TextButton(onClick = {
                        val receipt = "NEON WALLET · ДЕМО-ЧЕК\n${tx.title}\nСумма: ${money(tx.amount)}\nПолучатель: ${tx.recipient}\nБанк: ${tx.bank}\nДата: ${tx.date}\nID: ${tx.operationId}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, receipt) }, "Поделиться чеком"))
                    }) { Text("Поделиться", color = Muted) } }
                )
            }
        }
    }
}
