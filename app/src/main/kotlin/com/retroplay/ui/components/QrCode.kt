package com.retroplay.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodes {
    /** Matriz do QR code ([size] 0 = 1 módulo por pixel), com margem mínima. */
    fun matrix(text: String, size: Int = 0): BitMatrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )

    fun bitmap(text: String): Bitmap {
        val m = matrix(text)
        val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}

/** QR code nítido (sem suavização), em fundo branco para leitura fácil pela câmera. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(text) { QrCodes.bitmap(text).asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = text,
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
    )
}
