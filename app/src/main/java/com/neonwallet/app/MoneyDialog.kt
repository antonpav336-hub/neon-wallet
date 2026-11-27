package com.neonwallet.app

import android.content.Intent
import android.provider.ContactsContract
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MoneyDialog(
    kind: String,
    balance: Long,
    cardFrozen: Boolean,
    cardLimit: Long,
    onClose: () -> Unit,
    onBegin: () -> Boolean,
    onFinish: () -> Unit,
    onConfirm: (Long, String, String) -> Boolean,
    initialRecipient: String = ""
) {
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf(initialRecipient) }
    var bank by remember { mutableStateOf("NEON") }
    var message by remember { mutableStateOf("") }
    var contactList by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var showContacts by remember { mutableStateOf(false) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { contactList = readPhoneContacts(context); showContacts = true }
    }
    var stage by remember { mutableStateOf("editing") }
    var failure by remember { mutableStateOf<String?>(null) }
    var receiptId by remember { mutableStateOf("") }
    var receiptTime by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val parsedAmount = amount.toLongOrNull() ?: 0L
    val transfer = kind == "transfer"
    val action = when (kind) {
        "transfer" -> "Перевести"
        "withdraw" -> "Вывести"
        "card_payment" -> "Оплатить"
        else -> "Пополнить"
    }
    val favoritesPrefs = remember(context) {
        val email = context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE)
            .getString("current_email", "") ?: ""
        val accountKey = android.util.Base64.encodeToString(
            email.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        context.getSharedPreferences("neon_transfer_favorites_$accountKey", android.content.Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(favoritesPrefs.getStringSet("recipients", emptySet())?.toList()?.sorted() ?: emptyList<String>())
    }
    val liveError = if (amount.isNotBlank()) {
        operationFailureReason(kind, parsedAmount, balance, recipient, cardFrozen, cardLimit, bank = bank)
    } else null
    val canSend = stage == "editing" && amount.isNotBlank() && liveError == null

    BackHandler(enabled = true) {
        if (stage != "processing") onClose()
    }

    Dialog(
        onDismissRequest = { if (stage != "processing") onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg, shape = RoundedCornerShape(0.dp)) {
            Column(Modifier.fillMaxSize().background(Bg)) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, enabled = stage != "processing") {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (stage) {
                            "processing" -> "Обработка перевода"
                            "success" -> "Чек операции"
                            "error" -> "Операция отклонена"
                            else -> if (transfer) "Перевод" else action
                        },
                        color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Bolt, null, tint = Purple, modifier = Modifier.size(28.dp))
                }
                HorizontalDivider(color = White.copy(alpha = .07f))
                AnimatedContent(
                    targetState = stage,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "operation_stage"
                ) { current ->
                    when (current) {
                        "processing" -> ProcessingContent()
                        "success" -> ReceiptContent(
                            kind, parsedAmount, recipient, bank, receiptTime, receiptId,
                            onCopy = { clipboard.setText(AnnotatedString(receiptId)) },
                            onShare = {
                                val receipt = "NEON WALLET\nОперация: $action\nСумма: $parsedAmount ₽\nПолучатель: $recipient\nБанк: $bank\nСообщение: ${message.ifBlank { "—" }}\nВремя: $receiptTime\nID: $receiptId"
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, receipt)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Поделиться чеком"))
                            }
                        )
                        "error" -> ErrorContent(failure ?: "Проверь данные операции.")
                        else -> Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            if (transfer) {
                                Text("Получатель", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                        .background(Panel)
                                        .border(1.dp, Purple.copy(alpha = .35f), RoundedCornerShape(22.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(54.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Purple, Green))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(recipient.firstOrNull()?.uppercase() ?: "＋", color = Color.White,
                                            fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(recipient.ifBlank { "Выберите получателя" }, color = White,
                                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("Имя · телефон · NEON ID", color = Muted, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.Edit, null, tint = Purple, modifier = Modifier.size(18.dp))
                                }
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    (listOf("Alex", "Мама", "Самому себе") + favorites).distinct().forEach { name ->
                                        RecipientChip(name, recipient == name) { recipient = name; failure = null }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                            contactList = readPhoneContacts(context)
                                            showContacts = true
                                        } else contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, Purple.copy(alpha = .55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
                                ) {
                                    Icon(Icons.Default.Contacts, null, tint = Purple)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Выбрать из контактов")
                                }
                                OutlinedTextField(
                                    value = recipient,
                                    onValueChange = { recipient = it; failure = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Имя, телефон или NEON ID") },
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = Purple) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = neonFieldColors()
                                )
                                if (recipient.isNotBlank() && recipient.trim() !in favorites) {
                                    TextButton(onClick = {
                                        favorites = (favorites + recipient.trim()).distinct().take(12)
                                        favoritesPrefs.edit().putStringSet("recipients", favorites.toSet()).apply()
                                    }) {
                                        Icon(Icons.Default.StarBorder, null, tint = Purple)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Добавить в избранное", color = Purple)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Сообщение получателю", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = message,
                                        onValueChange = { message = it.take(120) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Необязательно") },
                                        placeholder = { Text("Например, спасибо!") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = neonFieldColors()
                                    )
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("🤝 Спасибо", "☕ За кофе", "🎁 На подарок").forEach { preset ->
                                            RecipientChip(preset, message == preset) { message = preset }
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Банк назначения", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("NEON", "ВТБ", "Сбер", "Т-Банк", "Альфа-Банк").forEach { item ->
                                            BankChip(item, bank == item) { bank = item; failure = null }
                                        }
                                    }
                                }
                            } else {
                                Text("Детали операции", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = recipient, onValueChange = { recipient = it; failure = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Получатель / назначение") },
                                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = neonFieldColors()
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                Text("Сумма, ₽", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    listOf(500L, 1000L, 3000L, 5000L).forEach { preset ->
                                        AmountChip(preset, parsedAmount == preset) {
                                            amount = preset.toString(); failure = null
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = amount,
                                    onValueChange = { amount = it.filter(Char::isDigit).take(9); failure = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Введите сумму") },
                                    leadingIcon = { Text("₽", color = Purple, fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                                    singleLine = true, shape = RoundedCornerShape(18.dp), colors = neonFieldColors()
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Доступно", color = Muted, fontSize = 12.sp)
                                    Text("$balance ₽", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    liveError ?: if (amount.isBlank()) "Укажи сумму для расчёта остатка."
                                    else "После операции останется: ${(balance - parsedAmount).coerceAtLeast(0)} ₽",
                                    color = if (liveError != null) Color(0xFFFF858D) else Green,
                                    fontSize = 12.sp, minLines = 1
                                )
                                if (failure != null) Text(failure!!, color = Color(0xFFFF858D), fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (stage == "editing") {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .background(Bg).padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!canSend) {
                                    failure = liveError ?: "Укажи сумму и получателя."
                                } else if (!onBegin()) {
                                    failure = "Операция уже обрабатывается."
                                } else {
                                    stage = "processing"
                                    scope.launch {
                                        try {
                                            delay(820)
                                            receiptId = "NW" + System.currentTimeMillis()
                                            receiptTime = SimpleDateFormat("dd MMM · HH:mm", Locale("ru")).format(Date())
                                            if (onConfirm(parsedAmount, recipient.trim(), bank)) {
                                                stage = "success"
                                            } else {
                                                failure = operationFailureReason(kind, parsedAmount, balance, recipient,
                                                    cardFrozen, cardLimit, bank = bank) ?: "Операция отклонена. Проверь данные."
                                                stage = "error"
                                            }
                                        } catch (_: Exception) {
                                            failure = "Не удалось завершить операцию. Попробуй ещё раз."
                                            stage = "error"
                                        } finally {
                                            onFinish()
                                        }
                                    }
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.fillMaxWidth().height(58.dp)
                                .shadow(if (canSend) 16.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = Purple),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple, disabledContainerColor = Panel,
                                contentColor = Color.White, disabledContentColor = Muted
                            )
                        ) {
                            Text(
                                if (parsedAmount > 0) "$action · $parsedAmount ₽  →" else "$action  →",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (stage == "success" || stage == "error") {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) { Text("Готово", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (showContacts) {
        AlertDialog(
            onDismissRequest = { showContacts = false },
            containerColor = Panel,
            title = { Text("Контакты", color = White, fontWeight = FontWeight.Bold) },
            text = {
                if (contactList.isEmpty()) Text("В телефонной книге пока нет контактов с номерами.", color = Muted)
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    contactList.forEach { (name, phone) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                recipient = phone
                                showContacts = false
                                failure = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Purple.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                                Text(name.firstOrNull()?.uppercase() ?: "?", color = Purple, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(name, color = White, fontSize = 14.sp)
                                Text(phone, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContacts = false }) { Text("Закрыть", color = Purple) } }
        )
    }
}

private fun readPhoneContacts(context: android.content.Context): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
    )
    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext() && result.size < 250) {
            val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty() else ""
            val phone = if (phoneIndex >= 0) it.getString(phoneIndex).orEmpty() else ""
            if (name.isNotBlank() && phone.isNotBlank()) result.add(name to phone)
        }
    }
    return result.distinctBy { it.second }
}

@Composable
private fun neonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White, cursorColor = Purple,
    focusedBorderColor = Purple, unfocusedBorderColor = Muted.copy(alpha = .5f),
    focusedLabelColor = Purple, unfocusedLabelColor = Muted
)

@Composable
private fun RecipientChip(name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(name, maxLines = 1) },
        leadingIcon = {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Purple.copy(alpha = .18f)),
                contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Purple.copy(alpha = .25f), selectedLabelColor = White, labelColor = Muted
        )
    )
}

@Composable
private fun BankChip(bank: String, selected: Boolean, onClick: () -> Unit) {
    val accent = when (bank) {
        "ВТБ" -> Color(0xFF54A7FF)
        "Сбер" -> Green
        "Т-Банк" -> Color.White
        "Альфа-Банк" -> Color(0xFFFF646E)
        else -> Purple
    }
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = .18f) else Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) accent else Muted.copy(alpha = .25f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Text(bank, color = if (selected) White else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AmountChip(value: Long, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(java.text.NumberFormat.getIntegerInstance(Locale("ru")).format(value) + " ₽", maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Purple.copy(alpha = .25f), selectedLabelColor = White, labelColor = Muted
        )
    )
}

@Composable
private fun ProcessingContent() {
    val transition = rememberInfiniteTransition(label = "processing_pulse")
    val pulse by transition.animateFloat(
        initialValue = .88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(560), repeatMode = RepeatMode.Reverse),
        label = "processing_scale"
    )
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(138.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().border(2.dp, Purple.copy(alpha = .5f), CircleShape))
            CircularProgressIndicator(color = Purple, strokeWidth = 4.dp, modifier = Modifier.size(92.dp))
            androidx.compose.material3.Icon(Icons.Default.Bolt, null, tint = Green, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Подготавливаем перевод", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Проверяем данные операции…", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReceiptContent(
    kind: String, amount: Long, recipient: String, bank: String, time: String, id: String,
    onCopy: () -> Unit, onShare: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(14.dp))
        OperationStatusMark(success = true)
        Spacer(Modifier.height(18.dp))
        Text(if (kind == "transfer") "Перевод отправлен" else "Операция выполнена",
            color = Green, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("$amount ₽", color = White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(22.dp))
        ReceiptLine("Получатель", recipient.ifBlank { "—" })
        ReceiptLine("Банк назначения", bank)
        ReceiptLine("Выполнено", time)
        ReceiptLine("ID операции", id)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Purple), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) {
            androidx.compose.material3.Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp)); Text("Скопировать ID")
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Purple), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) {
            androidx.compose.material3.Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp)); Text("Поделиться чеком")
        }
        Text("Операция обработана в NEON WALLET", color = Muted, fontSize = 10.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = White.copy(alpha = .07f))
}

@Composable
private fun ErrorContent(reason: String) {
    val shake by animateFloatAsState(targetValue = 1f, animationSpec = tween(420), label = "error_shake")
    Column(
        Modifier.fillMaxSize().padding(28.dp).graphicsLayer {
            translationX = kotlin.math.sin(shake * Math.PI * 8).toFloat() * (1f - shake) * 10f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OperationStatusMark(success = false)
        Spacer(Modifier.height(20.dp))
        Text("Не удалось выполнить", color = Color(0xFFFF858D), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(reason, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun OperationStatusMark(success: Boolean) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(success) { started = true }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(520), label = "status_mark_progress")
    Canvas(Modifier.size(94.dp).graphicsLayer {
        val wobble = if (success) 0f else kotlin.math.sin(progress * Math.PI * 8).toFloat() * (1f - progress) * 9f
        translationX = wobble
        val bounce = if (success) kotlin.math.sin(progress * Math.PI).toFloat() * .12f else 0f
        scaleX = (if (success) .86f + .14f * progress else 1f) + bounce
        scaleY = (if (success) .86f + .14f * progress else 1f) + bounce
    }) {
        val stroke = 5.dp.toPx()
        val tint = if (success) Green else Color(0xFFFF6675)
        drawCircle(tint.copy(alpha = .13f), style = Stroke(stroke))
        drawCircle(tint, style = Stroke(stroke))
        if (success) {
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .52f),
                androidx.compose.ui.geometry.Offset(size.width * .43f, size.height * .68f), stroke, cap = StrokeCap.Round)
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .43f, size.height * .68f),
                androidx.compose.ui.geometry.Offset(size.width * .77f, size.height * .32f), stroke, cap = StrokeCap.Round)
        } else {
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .34f),
                androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .66f), stroke, cap = StrokeCap.Round)
            drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .34f),
                androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .66f), stroke, cap = StrokeCap.Round)
        }
    }
}
