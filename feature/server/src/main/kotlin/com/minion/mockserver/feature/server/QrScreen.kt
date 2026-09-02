package com.minion.mockserver.feature.server

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.minion.mockserver.core.data.discovery.QrEncoder
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * The full-screen QR code (SPEC §8.5).
 *
 * Two things make it actually scannable, and both are why this is a destination rather than a
 * dialog: the screen brightness is forced to maximum for as long as it is shown, and the code is
 * drawn black-on-white regardless of theme. A dark-mode QR at 20% brightness is one most phone
 * cameras will not resolve, which turns a five-second handoff into typing an IP by hand.
 */
@Composable
internal fun QrScreen(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(density) { with(density) { Dimens.qrCodeSize.roundToPx() } }
    val bitmap = remember(url, sizePx) { QrEncoder().encode(url, sizePx) }

    // Restored on the way out, so the brightness override does not outlive the screen.
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(Dimens.space),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.server_qr_description, url),
                modifier = Modifier.size(Dimens.qrCodeSize),
            )
        }
        Text(
            text = url,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = Dimens.space),
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = Dimens.space)) {
            Text(stringResource(R.string.server_qr_close), color = Color.Black)
        }
    }
}
