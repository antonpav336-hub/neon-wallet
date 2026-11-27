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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.text.NumberFormat
import java.util.Locale


@Composable
internal fun AuthScreen(prefs: android.content.SharedPreferences, onSuccess: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(prefs.getString("current_email", "") ?: "") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF211C2A), Bg, Color(0xFF0B0A10))))) {
        Image(painterResource(R.drawable.bg_neon_auth), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.28f))
        Box(Modifier.align(Alignment.TopEnd).offset(x = 65.dp, y = (-65).dp).size(240.dp).background(Purple.copy(alpha = .14f), CircleShape).blur(65.dp))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = 80.dp).size(220.dp).background(Color(0xFF74628F).copy(alpha = .16f), CircleShape).blur(60.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).shadow(24.dp, RoundedCornerShape(25.dp), ambientColor = Purple, spotColor = Purple)
                .clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF5426A6)))),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = White, modifier = Modifier.size(43.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("NEON WALLET", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("ТВОИ ФИНАНСЫ. ТВОИ ПРАВИЛА.", color = Color(0xFFC6B8DD), fontSize = 10.sp, letterSpacing = 1.7.sp)
            Spacer(Modifier.height(38.dp))
            Text(if (register) "Создать аккаунт" else "С возвращением", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(if (register) "Зарегистрируй свой NEON ID" else "Войди, чтобы продолжить", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp, bottom = 24.dp))
            if (register) {
                AuthField(fullName, { fullName = it; error = "" }, "ФИО", Icons.Default.Person)
                Spacer(Modifier.height(12.dp))
            }
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
                    register && fullName.trim().split(Regex("\\s+")).size < 2 -> error = "Введи имя и фамилию полностью."
                    password.length < 8 -> error = "Пароль должен содержать минимум 8 символов."
                    register && password != confirm -> error = "Пароли не совпадают."
                    else -> {
                        if (password.length < 10) {
                            error = "Пароль должен содержать минимум 10 символов."
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        if (register) {
                                            val usernameBase = normalized.substringBefore("@")
                                                .replace(Regex("[^A-Za-z0-9_]"), "_").take(24)
                                            val username = if (usernameBase.length >= 3) usernameBase else "neon_$usernameBase"
                                            WalletApi.register(username, normalized, password)
                                        } else {
                                            WalletApi.login(normalized, password)
                                        }
                                    }
                                    val user = response.optJSONObject("user") ?: org.json.JSONObject()
                                    prefs.edit()
                                        .putString("current_email", normalized)
                                        .putString("access_token", response.getString("access_token"))
                                        .putString("account_id", user.optString("account_id"))
                                        .putString("server_username", user.optString("username"))
                                        .apply { if (register) putString("name_$normalized", fullName.trim()) }
                                        .putBoolean("signed_in", true)
                                        .apply()
                                    onSuccess()
                                } catch (e: Exception) {
                                    error = e.message ?: "Не удалось связаться с сервером. Проверь интернет."
                                } finally {
                                    busy = false
                                }
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
            Text("Аккаунт, виртуальный баланс и история операций синхронизируются через защищённый сервер NEON.",
                color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun AuthField(value: String, onValueChange: (String) -> Unit, label: String,
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

internal fun passwordHash(password: String, salt: ByteArray): ByteArray {
    val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120000, 256)
    return try { javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
    finally { spec.clearPassword() }
}

internal fun verifyPassword(password: String, saved: String): Boolean {
    return try {
        val parts = saved.split(":")
        if (parts.size != 2) return false
        val salt = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val expected = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        java.security.MessageDigest.isEqual(passwordHash(password, salt), expected)
    } catch (_: Exception) { false }
}
