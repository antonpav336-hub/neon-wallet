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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
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
internal fun MoneyDialog(
    kind: String,
    balance: Long,
    cardFrozen: Boolean,
    cardLimit: Long,
    onClose: () -> Unit,
    onBegin: () -> Boolean,
    onConfirm: (Long, String, String) -> Boolean
) {
    var amount by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf("NEON") }
    var stage by remember { mutableStateOf("editing") }
    var failure by remember { mutableStateOf<String?>(null) }
    var receiptId by remember { mutableStateOf("") }
    var receiptTime by remember { mutableStateOf("") }
    val parsedAmount = amount.toLongOrNull() ?: 0L
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favoritePrefs = remember(context) { val email = context.getSharedPreferences("neon_auth_demo", android.content.Context.MODE_PRIVATE).getString("current_email", "") ?: ""; val key = android.util.Base64.encodeToString(email.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING); context.getSharedPreferences("neon_transfer_favorites_$key", android.content.Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(favoritePrefs.getStringSet("recipients", mutableSetOf<String>())?.toList()?.sorted() ?: emptyList<String>()) }
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
                        val pulseTransition = rememberInfiniteTransition(label = "processing_pulse")
                        val pulse by pulseTransition.animateFloat(initialValue = 0.94f, targetValue = 1.08f, animationSpec = infiniteRepeatable(animation = tween(520), repeatMode = RepeatMode.Reverse), label = "processing_pulse")
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
                        Text(if (who.isBlank()) "Демо-пополнение" else "$who · $selectedBank", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Выполнено: " + receiptTime, color = Muted, fontSize = 10.sp)
                        Text("Банк назначения: " + selectedBank, color = Muted, fontSize = 11.sp)
                        Text("ID операции: " + receiptId, color = Muted, fontSize = 10.sp)
                        OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(receiptId)) }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Скопировать ID")
                        }
                        OutlinedButton(onClick = {
                            val receipt = "NEON WALLET · ДЕМО-ЧЕК\n" + action + ": " + money(parsedAmount) + "\nПолучатель: " + who + "\nБанк: " + selectedBank + "\nID: " + receiptId + "\n" + receiptTime + "\nРеальный платёж не выполнялся."
                            val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, receipt) }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться чеком"))
                        }) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Поделиться чеком")
                        }
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
                            Text("Банк назначения", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("NEON", "ВТБ", "Сбер", "Т-Банк", "Альфа-Банк", "По номеру / ID").forEach { bank ->
                                    FilterChip(selected = selectedBank == bank, onClick = { selectedBank = bank }, label = { Text(bank) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple.copy(alpha = .28f), selectedLabelColor = White, labelColor = Muted))
                                }
                            }
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
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(500, 1000, 3000, 5000).forEach { preset ->
                                AssistChip(onClick = { amount = preset.toString(); failure = null }, label = { Text(preset.toString() + " ₽") })
                            }
                        }
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(9); failure = null }, label = { Text("Сумма в ₽") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedLabelColor = Purple, unfocusedLabelColor = Muted))
                        val liveError = if (amount.isNotBlank()) operationFailureReason(kind, parsedAmount, balance, who, cardFrozen, cardLimit) else null
                        if (liveError != null) Text(liveError, color = Color(0xFFFF929B), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        else if (failure != null) Text(failure!!, color = Color(0xFFFF858D), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
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
                        if (!onBegin()) { failure = "Операция уже обрабатывается."; return@TextButton }
                        stage = "processing"
                        scope.launch {
                            delay(820)
                            val accepted = onConfirm(parsedAmount, who.trim(), selectedBank)
                            if (accepted) {
                                receiptId = "NW" + System.currentTimeMillis().toString()
                                receiptTime = java.text.SimpleDateFormat("dd MMM · HH:mm", Locale("ru")).format(java.util.Date())
                                stage = "success"
                            } else {
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
internal fun OperationStatusMark(success: Boolean) {
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
