package com.neonwallet.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale


private object WalletSession {
    // Remains unlocked while the process is alive; reset when the task is actually finished.
    var unlocked: Boolean = false
}

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) WalletSession.unlocked = false
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeonWalletApp() }
    }
}

@Composable
fun NeonWalletApp() {
    val context = LocalContext.current
    val authPrefs = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE) }
    var signedIn by remember { mutableStateOf(!authPrefs.getString("access_token", null).isNullOrBlank()) }
    var pinUnlocked by remember { mutableStateOf(WalletSession.unlocked) }
    if (!signedIn) {
        AuthScreen(authPrefs) {
            authPrefs.edit().putBoolean("signed_in", true).apply()
            signedIn = true
            WalletSession.unlocked = false
            pinUnlocked = false
        }
    } else if (!pinUnlocked) {
        PinGateScreen(authPrefs, setup = !authPrefs.contains("pin_hash")) {
            WalletSession.unlocked = true
            pinUnlocked = true
        }
    } else {
        WalletMain {
            authPrefs.edit().putBoolean("signed_in", false).remove("access_token").apply()
            signedIn = false
            WalletSession.unlocked = false
            pinUnlocked = false
        }
    }
}

@Composable
private fun WalletMain(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val authPrefs = remember(context) { context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE) }
    val accountEmail = authPrefs.getString("current_email", "") ?: ""
    val token = authPrefs.getString("access_token", null).orEmpty()
    val deviceId = remember(accountEmail) {
        authPrefs.getString("wallet_device_id", null) ?: java.util.UUID.randomUUID().toString().also {
            authPrefs.edit().putString("wallet_device_id", it).apply()
        }
    }
    val accountId = authPrefs.getString("account_id", "").orEmpty()
    val accountFullName = authPrefs.getString("name_$accountEmail", accountEmail.substringBefore("@")) ?: accountEmail.substringBefore("@")
    val accountKey = remember(accountEmail) { android.util.Base64.encodeToString(accountEmail.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING) }
    val prefs = remember(context, accountKey) { context.getSharedPreferences("neon_wallet_demo_$accountKey", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var balance by remember(accountKey) { mutableLongStateOf(0L) }
    var showBalance by remember(accountKey) { mutableStateOf(prefs.getBoolean("show_balance", true)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var qrRecipient by remember { mutableStateOf("") }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        dialog = if (granted) "qr" else "camera_permission"
    }
    var txs by remember(accountKey) { mutableStateOf(emptyList<Tx>()) }
    var serverMessage by remember { mutableStateOf<String?>(null) }
    var cardFrozen by remember(accountKey) { mutableStateOf(prefs.getBoolean("card_frozen", false)) }
    var cardLimit by remember(accountKey) { mutableLongStateOf(prefs.getLong("card_limit", 50000L)) }
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedReceipt by remember { mutableStateOf<Tx?>(null) }
    var casinoRequests by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedCasinoRequest by remember { mutableStateOf<JSONObject?>(null) }
    var casinoApprovalPin by remember { mutableStateOf("") }
    var casinoApprovalError by remember { mutableStateOf("") }
    suspend fun refreshServerData() {
        val data = withContext(Dispatchers.IO) {
            Triple(WalletApi.wallet(token), WalletApi.transactions(token), WalletApi.settings(token))
        }
        balance = data.first.optLong("balance_kopecks", 0L) / 100L
        cardFrozen = data.third.optBoolean("card_frozen", false)
        cardLimit = data.third.optLong("card_limit_kopecks", 5_000_000L) / 100L
        val items = data.second.optJSONArray("items") ?: JSONArray()
        txs = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val incoming = item.optString("recipient_account_id") == accountId ||
                item.optString("kind") == "signup_bonus"
            val rawAmount = item.optLong("amount_kopecks", 0L) / 100L
            val instant = try { java.time.Instant.parse(item.optString("created_at")) } catch (_: Exception) { java.time.Instant.now() }
            val timestamp = instant.toEpochMilli()
            val date = java.text.SimpleDateFormat("dd MMM · HH:mm", Locale("ru")).format(java.util.Date(timestamp))
            val otherParty = if (incoming) item.optString("sender_account_id") else item.optString("recipient_account_id")
            Tx(
                item.optString("description", "Операция NEON"), date,
                if (incoming) rawAmount else -rawAmount, incoming,
                otherParty, "Успешно", timestamp,
                item.optString("kind", "transfer"), "NEON",
                item.optString("id").take(12)
            )
        }
        val pending = withContext(Dispatchers.IO) { WalletApi.pendingCasinoDeposits(token) }
        val requestItems = pending.optJSONArray("items") ?: JSONArray()
        casinoRequests = (0 until requestItems.length()).map { requestItems.getJSONObject(it) }
        if (selectedCasinoRequest == null && casinoRequests.isNotEmpty()) {
            selectedCasinoRequest = casinoRequests.first()
            casinoApprovalPin = ""
            casinoApprovalError = ""
        }
    }
    LaunchedEffect(token, deviceId) {
        var deviceRegistered = false
        while (true) {
            try {
                if (!deviceRegistered) {
                    val publicKey = withContext(Dispatchers.IO) { WalletDeviceSigner.publicKeyBase64() }
                    withContext(Dispatchers.IO) { WalletApi.registerDevice(token, deviceId, publicKey) }
                    deviceRegistered = true
                }
                refreshServerData()
                serverMessage = null
            } catch (e: Exception) {
                deviceRegistered = false
                serverMessage = e.message ?: "Не удалось загрузить данные с сервера."
            }
            delay(12000)
        }
    }
    SideEffect {
        prefs.edit().putBoolean("show_balance", showBalance)
            .putBoolean("card_frozen", cardFrozen).putLong("card_limit", cardLimit).apply()
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
                                { if (cardFrozen) dialog = "card_frozen" else dialog = "transfer" }, { if (cardFrozen) dialog = "card_frozen" else dialog = "withdraw" }, txs, { tab = Tab.CARDS }, { tab = Tab.HISTORY },
                                { selectedReceipt = it })
                            Tab.PAYMENTS -> Payments(balance, cardFrozen, cardLimit, { if (cardFrozen) dialog = "card_frozen" else dialog = "transfer" }, { dialog = "topup" }, { if (cardFrozen) dialog = "card_frozen" else dialog = "withdraw" }) {
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) dialog = "qr"
                                else cameraPermission.launch(android.Manifest.permission.CAMERA)
                            }
                            Tab.CARDS -> Cards(cardFrozen, cardLimit, accountFullName, {
                                val next = !cardFrozen
                                cardFrozen = next
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { WalletApi.updateSettings(token, next, cardLimit) }
                                        serverMessage = null
                                    } catch (e: Exception) {
                                        cardFrozen = !next
                                        serverMessage = e.message ?: "Не удалось сохранить состояние карты на сервере."
                                    }
                                }
                            }, { value ->
                                val nextLimit = value.coerceAtLeast(0L)
                                cardLimit = nextLimit
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { WalletApi.updateSettings(token, cardFrozen, nextLimit) }
                                        serverMessage = null
                                    } catch (e: Exception) {
                                        serverMessage = e.message ?: "Не удалось сохранить лимит карты на сервере."
                                    }
                                }
                            }) { if (canUseCard(cardFrozen)) dialog = "card_payment" }
                            Tab.HISTORY -> History(txs) { selectedReceipt = it }
                            Tab.PROFILE -> Profile(accountEmail, accountFullName, accountId, onSignOut)
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
                     "card_frozen" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        containerColor = Panel,
                        title = { Text("Карта заморожена", color = White) },
                        text = { Text("Исходящие операции отключены. Разморозь карту во вкладке «Карты», чтобы снова переводить, выводить и оплачивать.", color = Muted) },
                        confirmButton = { TextButton(onClick = { dialog = null; tab = Tab.CARDS }) { Text("К картам", color = Purple) } },
                        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Закрыть", color = Muted) } }
                    )
                    "camera_permission" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        containerColor = Panel,
                        title = { Text("Нужен доступ к камере", color = White) },
                        text = { Text("Разреши доступ к камере Android, чтобы сканировать QR-коды.", color = Muted) },
                        confirmButton = { TextButton(onClick = { dialog = null; cameraPermission.launch(android.Manifest.permission.CAMERA) }) { Text("Разрешить", color = Purple) } },
                        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Позже", color = Muted) } }
                    )
                    else -> MoneyDialog(kind, balance, cardFrozen, cardLimit, onClose = { if (!isSubmitting) { dialog = null; qrRecipient = "" } },
                    onBegin = { if (canStartOperation(isSubmitting)) { isSubmitting = true; true } else false },
                    onFinish = { isSubmitting = false }, initialRecipient = qrRecipient) { amount, recipient, bank ->
                    if (kind != "transfer") {
                        serverMessage = when (kind) {
                            "topup" -> "Пополнение появится после подключения безопасного серверного механизма зачисления виртуальных средств."
                            "withdraw" -> "Вывод средств не подключён: кошелёк использует только внутренние виртуальные рубли."
                            else -> "Виртуальная карта пока не подключена к серверным платежам."
                        }
                        null
                    } else if (!canSubmitMoneyOperation(kind, amount, balance, recipient, cardFrozen, cardLimit, bank = bank)) {
                        null
                    } else {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                WalletApi.transfer(token, recipient.trim(), amount, "Перевод NEON · $bank")
                            }
                            refreshServerData()
                            serverMessage = null
                            result.optString("transaction_id").takeIf { it.isNotBlank() }
                        } catch (e: Exception) {
                            serverMessage = e.message ?: "Сервер отклонил перевод."
                            null
                        }
                    }
                }
                }
            }
            serverMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { serverMessage = null },
                    containerColor = Panel,
                    title = { Text("NEON WALLET", color = White, fontWeight = FontWeight.Bold) },
                    text = { Text(message, color = Muted) },
                    confirmButton = { TextButton(onClick = { serverMessage = null }) { Text("Понятно", color = Purple) } }
                )
            }
            selectedCasinoRequest?.let { request ->
                val requestId = request.optString("request_id")
                val amountKopecks = request.optLong("amount_kopecks", 0L)
                val amountLabel = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).format(amountKopecks / 100.0)
                AlertDialog(
                    onDismissRequest = { selectedCasinoRequest = null; casinoApprovalPin = ""; casinoApprovalError = "" },
                    containerColor = Panel,
                    title = { Text("Запрос от NEON CASINO", color = White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Перевести $amountLabel с основного баланса NEON WALLET на игровой баланс казино?", color = White)
                            Text("Списание произойдёт только после твоего подтверждения. Запрос действует 15 минут.", color = Muted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = casinoApprovalPin,
                                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) casinoApprovalPin = it },
                                label = { Text("Подтверди PIN-код") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White, unfocusedTextColor = White,
                                    focusedBorderColor = Purple, unfocusedBorderColor = Muted,
                                    focusedLabelColor = Purple, unfocusedLabelColor = Muted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (casinoApprovalError.isNotBlank()) Text(casinoApprovalError, color = Color(0xFFFF858D), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = casinoApprovalPin.length >= 4 && !isSubmitting,
                            onClick = {
                                if (!verifyPin(authPrefs, casinoApprovalPin)) {
                                    casinoApprovalPin = ""
                                    casinoApprovalError = "Неверный PIN-код. Проверь и попробуй ещё раз."
                                } else {
                                    isSubmitting = true
                                    scope.launch {
                                        try {
                                            val challenge = request.optString("approval_challenge")
                                            val signedMessage = "NEON-CASINO-DEPOSIT\n$requestId\n$amountKopecks\n$challenge"
                                            val signature = withContext(Dispatchers.IO) {
                                                WalletDeviceSigner.signBase64(signedMessage)
                                            }
                                            val result = withContext(Dispatchers.IO) {
                                                WalletApi.approveCasinoDeposit(token, requestId, deviceId, signature)
                                            }
                                            casinoApprovalPin = ""
                                            casinoApprovalError = ""
                                            selectedCasinoRequest = null
                                            refreshServerData()
                                            serverMessage = if (result.optString("status") == "approved")
                                                "Перевод подтверждён. NEON CASINO пополнен на $amountLabel."
                                            else "Срок действия запроса истёк. Деньги не списаны."
                                        } catch (e: Exception) {
                                            casinoApprovalPin = ""
                                            casinoApprovalError = e.message ?: "Не удалось подтвердить перевод."
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                }
                            }
                        ) { Text(if (isSubmitting) "Обработка…" else "Подтвердить перевод", color = Purple) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = {
                                    isSubmitting = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { WalletApi.rejectCasinoDeposit(token, requestId) }
                                            selectedCasinoRequest = null
                                            casinoApprovalPin = ""
                                            casinoApprovalError = ""
                                            refreshServerData()
                                        } catch (e: Exception) {
                                            casinoApprovalError = e.message ?: "Не удалось отклонить запрос."
                                        } finally { isSubmitting = false }
                                    }
                                }
                            ) { Text("Отклонить", color = Color(0xFFFF8C9B)) }
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = { selectedCasinoRequest = null; casinoApprovalPin = ""; casinoApprovalError = "" }
                            ) { Text("Позже", color = Muted) }
                        }
                    }
                )
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
                        }
                    },
                    confirmButton = { TextButton(onClick = { selectedReceipt = null }) { Text("Готово", color = Purple) } },
                    dismissButton = { TextButton(onClick = {
                        val receipt = "NEON WALLET\n${tx.title}\nСумма: ${money(tx.amount)}\nПолучатель: ${tx.recipient}\nБанк: ${tx.bank}\nДата: ${tx.date}\nID: ${tx.operationId}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, receipt) }, "Поделиться чеком"))
                    }) { Text("Поделиться", color = Muted) } }
                )
            }
        }
    }
}
