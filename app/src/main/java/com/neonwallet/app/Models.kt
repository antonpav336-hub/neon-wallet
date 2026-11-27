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


internal data class Tx(
    val title: String,
    val date: String,
    val amount: Long,
    val incoming: Boolean,
    val recipient: String = "",
    val status: String = "Успешно",
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = if (incoming) "topup" else "transfer",
    val bank: String = "NEON",
    val operationId: String = ""
)
internal enum class Tab { HOME, PAYMENTS, CARDS, HISTORY, PROFILE }

internal fun money(value: Long): String = NumberFormat.getNumberInstance(Locale("ru", "RU")).format(kotlin.math.abs(value)) + " ₽"

internal fun loadTransactions(prefs: android.content.SharedPreferences): List<Tx> {
    val stored = prefs.getString("transactions", null)
    if (stored.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(stored)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Tx(item.getString("title"), item.getString("date"), item.getLong("amount"), item.getBoolean("incoming"), item.optString("recipient", ""), item.optString("status", "Успешно"), item.optLong("timestamp", 0L), item.optString("kind", if (item.getBoolean("incoming")) "topup" else "transfer"), item.optString("bank", "NEON"), item.optString("operationId", ""))
        }
    } catch (_: Exception) { emptyList() }
}
