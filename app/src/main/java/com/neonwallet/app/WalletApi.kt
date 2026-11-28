package com.neonwallet.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal object WalletApi {
    private const val BASE_URL = "https://neon-wallet-api-rpkc.onrender.com"

    private fun request(method: String, path: String, token: String? = null, body: JSONObject? = null, idempotencyKey: String? = null): JSONObject {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            idempotencyKey?.let { setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                val detail = json.optString("detail").ifBlank { "Ошибка сервера ($code)" }
                throw IllegalStateException(detail)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun register(username: String, email: String, password: String) =
        request("POST", "/v1/auth/register", body = JSONObject().put("username", username).put("email", email).put("password", password))

    fun login(email: String, password: String) =
        request("POST", "/v1/auth/login", body = JSONObject().put("email", email).put("password", password))

    fun wallet(token: String) = request("GET", "/v1/wallet", token)
    fun transactions(token: String) = request("GET", "/v1/transactions?limit=100", token)
    fun pendingCasinoDeposits(token: String) = request("GET", "/v1/casino/deposit-requests/pending", token)
    fun approveCasinoDeposit(token: String, requestId: String) =
        request("POST", "/v1/casino/deposit-requests/$requestId/approve", token, JSONObject())
    fun rejectCasinoDeposit(token: String, requestId: String) =
        request("POST", "/v1/casino/deposit-requests/$requestId/reject", token, JSONObject())
    fun settings(token: String) = request("GET", "/v1/wallet/settings", token)
    fun updateSettings(token: String, frozen: Boolean, limitRubles: Long) = request(
        "PUT", "/v1/wallet/settings", token,
        JSONObject().put("card_frozen", frozen).put("card_limit_kopecks", limitRubles * 100L)
    )

    fun transfer(token: String, recipientAccountId: String, amountRubles: Long, description: String): JSONObject {
        require(recipientAccountId.matches(Regex("[0-9]{6,12}"))) { "Для перевода нужен 6–12-значный NEON ID получателя." }
        require(amountRubles > 0 && amountRubles <= 1_000_000) { "Некорректная сумма перевода." }
        return request(
            "POST", "/v1/transfers", token,
            JSONObject()
                .put("recipient_account_id", recipientAccountId)
                .put("amount_kopecks", amountRubles * 100L)
                .put("description", description.ifBlank { "Перевод NEON" }),
            "neon-android-" + UUID.randomUUID().toString()
        )
    }
}
