package com.minion.mockserver.core.data.discovery

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes the server's base URL as a QR code (SPEC §8.5).
 *
 * Encode only. `zxing:android-core` and the embedded scanner would bring a camera dependency and
 * a camera permission for a feature this app does not have — nothing here ever *reads* a QR code.
 *
 * The payload is the plain base URL (`http://192.168.1.45:8080`) rather than a deep link or a
 * custom scheme, so scanning it with any phone's stock camera opens a browser at the mock server.
 */
@Singleton
class QrEncoder @Inject constructor() {

    /**
     * Renders [content] as a square bitmap [sizePx] on a side, or `null` if it will not encode.
     *
     * `null` rather than an exception: the QR is a convenience next to an address that is already
     * on screen and copyable, so failing to draw it should dim a button, not take down a screen.
     */
    fun encode(content: String, sizePx: Int): Bitmap? = try {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                // A URL scanned across a desk needs to survive glare and a phone that is not quite
                // still; M is the usual trade of density against robustness.
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
                EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            ),
        )

        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        // Always black-on-white regardless of theme: a scanner reads reflectance, and an inverted
        // QR on a dark background is a QR most cameras will not resolve.
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        /** Modules of white border. Below four, scanners start to miss the finder patterns. */
        const val QUIET_ZONE_MODULES = 2
    }
}
