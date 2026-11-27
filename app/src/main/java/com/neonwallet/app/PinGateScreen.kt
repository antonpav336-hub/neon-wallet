package com.neonwallet.app

import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal fun makePinHash(pin: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, 180000, 256)
    return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
    finally { spec.clearPassword() }
}

internal fun savePin(prefs: SharedPreferences, pin: String) {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val hash = makePinHash(pin, salt)
    prefs.edit()
        .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
        .apply()
}

internal fun verifyPin(prefs: SharedPreferences, pin: String): Boolean {
    return try {
        val salt = Base64.decode(prefs.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", "") ?: "", Base64.NO_WRAP)
        MessageDigest.isEqual(makePinHash(pin, salt), expected)
    } catch (_: Exception) { false }
}

@Composable
internal fun PinGateScreen(prefs: SharedPreferences, setup: Boolean, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val title = if (setup) "Создай PIN-код" else "С возвращением"
    val subtitle = if (setup && firstPin.isNotEmpty()) "Повтори PIN-код" else if (setup) "Придумай код из 4–6 цифр" else "Введи PIN-код для входа"
    Column(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF10091D), Bg, Color(0xFF090711))))
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF5426A6)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bolt, null, tint = White, modifier = Modifier.size(43.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("NEON WALLET", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(34.dp))
        Text(title, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 25.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(if (setup && firstPin.isNotEmpty()) firstPin.length else 6) { index ->
                Box(Modifier.size(13.dp).clip(CircleShape).background(
                    if (index < pin.length) Purple else Color(0xFF453A55)
                ))
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF858D), fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
        Spacer(Modifier.height(28.dp))
        val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Box(Modifier.size(76.dp).clip(CircleShape)
                        .background(if (key.isBlank()) Color.Transparent else Panel)
                        .then(if (key.isBlank()) Modifier else Modifier.clickable {
                            error = ""
                            when (key) {
                                "⌫" -> pin = pin.dropLast(1)
                                else -> if (pin.length < 6) pin += key
                            }
                        }), contentAlignment = Alignment.Center) {
                        if (key.isNotBlank()) Text(key, color = White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (setup) {
                    if (firstPin.isEmpty()) {
                        if (pin.length < 4) error = "PIN должен содержать минимум 4 цифры."
                        else { firstPin = pin; pin = ""; error = "" }
                    } else if (pin != firstPin) {
                        firstPin = ""; pin = ""; error = "Коды не совпали. Начни заново."
                    } else {
                        savePin(prefs, pin); onUnlocked()
                    }
                } else {
                    if (verifyPin(prefs, pin)) onUnlocked()
                    else { pin = ""; error = "Неверный PIN-код." }
                }
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple, disabledContainerColor = Panel)
        ) { Text(if (setup && firstPin.isEmpty()) "Продолжить" else "Подтвердить", color = White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(20.dp))
        Text("PIN защищает доступ к приложению на этом устройстве.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
