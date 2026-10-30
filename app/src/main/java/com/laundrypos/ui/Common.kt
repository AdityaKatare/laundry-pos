package com.laundrypos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.laundrypos.AppContainer
import com.laundrypos.data.OrderStatus
import com.laundrypos.notify.WhatsApp

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

class WhatsAppSender(private val context: Context) {
    var pendingLink by mutableStateOf<String?>(null)

    fun send(phone: String, text: String) {
        val pkg = WhatsApp.installedPackage(context)
        if (pkg != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhatsApp.appLink(phone, text)))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        pendingLink = WhatsApp.webLink(phone, text)
    }
}

@Composable
fun rememberWhatsApp(): WhatsAppSender {
    val context = LocalContext.current
    val sender = remember { WhatsAppSender(context) }
    sender.pendingLink?.let { link ->
        WhatsAppQrDialog(link, onDismiss = { sender.pendingLink = null })
    }
    return sender
}

@Composable
private fun WhatsAppQrDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val qr = remember(link) { qrBitmap(link, 640) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send on WhatsApp") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WhatsApp isn't installed on this device. Scan with the shop phone to open the chat with the message ready, then tap Send.")
                Image(qr.asImageBitmap(), contentDescription = "WhatsApp link QR code", modifier = Modifier.size(280.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDismiss()
            }) { Text("Open in browser") }
        },
    )
}

fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun StatusBadge(status: OrderStatus) {
    val (label, color) = when (status) {
        OrderStatus.RECEIVED -> "Received" to Color(0xFF1565C0)
        OrderStatus.READY -> "Ready" to Color(0xFF2E7D32)
        OrderStatus.DELIVERED -> "Delivered" to Color(0xFF616161)
        OrderStatus.CANCELLED -> "Cancelled" to Color(0xFFC62828)
    }
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
