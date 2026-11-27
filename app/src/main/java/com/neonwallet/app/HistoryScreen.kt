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
internal fun History(txs: List<Tx>, onTransactionClick: (Tx) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf("all") }
    val filtered = txs.filter {
        (selectedKind == "all" || (selectedKind == "outgoing" && !it.incoming) || it.kind == selectedKind) &&
            (it.title.contains(query, true) || it.recipient.contains(query, true) || it.status.contains(query, true))
    }
    val income = txs.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = txs.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
    val dayFormat = java.text.SimpleDateFormat("yyyyMMdd", Locale("ru"))
    val today = dayFormat.format(java.util.Date())
    val yesterday = dayFormat.format(java.util.Date(System.currentTimeMillis() - 86400000L))
    val grouped = filtered.groupBy { if (it.timestamp > 0L) dayFormat.format(java.util.Date(it.timestamp)) else "older" }
    Column(Modifier.fillMaxSize()) {
        Header()
        Text("История операций", Modifier.padding(horizontal = 22.dp), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Движение средств и аналитика", Modifier.padding(start = 22.dp, top = 5.dp, bottom = 14.dp), color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Поступления", income, Green, Modifier.weight(1f))
            SummaryTile("Расходы", expense, Color(0xFFFF9292), Modifier.weight(1f))
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), singleLine = true, placeholder = { Text("Поиск по операции или получателю", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Purple, unfocusedBorderColor = Muted))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Все", "topup" to "Поступления", "outgoing" to "Списания", "transfer" to "Переводы", "card_payment" to "Оплаты").forEach { (key, label) ->
                FilterChip(selected = selectedKind == key, onClick = { selectedKind = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple.copy(alpha = .25f), selectedLabelColor = White, labelColor = Muted))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            grouped.forEach { (day, dayTxs) ->
                item {
                    val label = when (day) { today -> "Сегодня"; yesterday -> "Вчера"; "older" -> "Ранее"; else -> day }
                    Text(label, Modifier.padding(start = 22.dp, top = 15.dp, bottom = 5.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                items(dayTxs) { tx -> TransactionRow(tx) { onTransactionClick(tx) } }
            }
            if (filtered.isEmpty()) item { Text("Операций не найдено", Modifier.fillMaxWidth().padding(28.dp), color = Muted) }
        }
    }
}
