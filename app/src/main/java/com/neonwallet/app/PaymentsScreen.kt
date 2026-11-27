package com.neonwallet.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Payments(
    balance: Long,
    cardFrozen: Boolean,
    cardLimit: Long,
    onTransfer: () -> Unit,
    onTopup: () -> Unit,
    onWithdraw: () -> Unit,
    onScanQr: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Платежи и переводы", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать QR", tint = Purple, modifier = Modifier.size(28.dp).clickable(onClick = onScanQr))
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PromoTile("NEON Переводы", "Быстро и удобно", Icons.Default.Bolt, onTransfer)
                PromoTile("Мои шаблоны", "Частые операции", Icons.Default.Bookmarks, onTransfer)
                PromoTile("Пополнение", "Демо-счёт", Icons.Default.AddCard, onTopup)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Panel).padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Перевести", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Bg).border(1.dp, Purple.copy(alpha = .35f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onTransfer).padding(horizontal = 15.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Purple, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Телефон, карта или счёт", color = Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.QrCodeScanner, null, tint = White)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TransferShortcut("Между\nсчетами", Icons.Default.SwapHoriz, onTransfer)
                    TransferShortcut("По номеру\nкарты", Icons.Default.CreditCard, onTransfer)
                    TransferShortcut("Из другого\nбанка", Icons.Default.South, onTopup)
                    TransferShortcut("По\nреквизитам", Icons.Default.ReceiptLong, onTransfer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniPromo("В другую страну", "Международный перевод", Icons.Default.Public, onTransfer, Modifier.weight(1f))
                    MiniPromo("Пополнить извне", "Демо-зачисление", Icons.Default.Language, onTopup, Modifier.weight(1f))
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Panel)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text("Регулярные платежи", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TransferShortcut("Мобильная\nсвязь", Icons.Default.Smartphone, onWithdraw)
                    TransferShortcut("Интернет, ТВ", Icons.Default.Wifi, onWithdraw)
                    TransferShortcut("Коммунальные", Icons.Default.Home, onWithdraw)
                    TransferShortcut("Шаблоны", Icons.Default.CalendarMonth, onTransfer)
                }
                HorizontalDivider(color = White.copy(alpha = .08f))
                Text("Оплатить", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                PaymentCategory("Мобильная связь", "Демо-оплата услуг", Icons.Default.PhoneAndroid, onWithdraw)
                PaymentCategory("Коммунальные платежи", "Дом и ЖКХ", Icons.Default.Home, onWithdraw)
                PaymentCategory("Интернет, ТВ, телефония", "Связь и провайдеры", Icons.Default.Wifi, onWithdraw)
                PaymentCategory("Налоги, штрафы, госплатежи", "Государственные услуги", Icons.Default.AccountBalance, onWithdraw)
                PaymentCategory("Транспорт и парковки", "Городские сервисы", Icons.Default.DirectionsCar, onWithdraw)
                PaymentCategory("Образование", "Детские сады, школы, вузы", Icons.Default.School, onWithdraw)
                PaymentCategory("Заправки", "Топливо и АЗС", Icons.Default.LocalGasStation, onWithdraw)
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Purple.copy(alpha = .24f), Panel)))
                    .border(1.dp, Purple.copy(alpha = .25f), RoundedCornerShape(20.dp)).padding(17.dp)
            ) {
                Text("Счёт NEON", color = Muted, fontSize = 12.sp)
                Text(money(balance), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Доступный демонстрационный баланс", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Text("Лимит карты: " + money(cardLimit), color = White, fontSize = 12.sp)
                Text(if (cardFrozen) "Карта заморожена" else "Карта активна",
                    color = if (cardFrozen) Color(0xFFFFB86B) else Green, fontSize = 12.sp)
            }
            Text(
                "Все операции NEON WALLET демонстрационные. Переводы в реальные банки и оплата услуг не подключены.",
                Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                color = Muted, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PromoTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.width(178.dp).height(150.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF51475F), Color(0xFF25212D))))
            .border(1.dp, Purple.copy(alpha = .24f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(R.drawable.bg_neon_payments), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.26f))
        Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-34).dp).size(95.dp)
            .background(Color(0xFFD7C5F5).copy(alpha = .10f), CircleShape))
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = Color(0xFFE0D4F6), modifier = Modifier.size(30.dp))
            Column {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TransferShortcut(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF292632)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (icon == Icons.Default.SwapHoriz) Purple else White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = White, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun MiniPromo(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(17.dp)).background(Color(0xFF292632))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 9.sp)
        }
        Icon(icon, null, tint = Purple, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PaymentCategory(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF292632)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}
