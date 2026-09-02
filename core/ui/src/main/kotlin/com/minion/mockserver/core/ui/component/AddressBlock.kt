package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.ui.R

/**
 * The hero of the server screen: where clients should point (SPEC §8.5, §10.3).
 *
 * Three things share this card, and each answers a different question:
 *
 * - The **winning address**, large and monospaced, because it is read across a desk and typed
 *   into a laptop by hand.
 * - **`localhost`**, always, on its own row — it answers "how does another app on this phone
 *   reach it", and it is the only row still correct when there is no network at all.
 * - **Everything else**, behind an expander, because a phone with Wi-Fi, a hotspot and a VPN has
 *   four addresses and only one of them is the one anybody wants.
 *
 * With no usable interface the card says so explicitly rather than showing a blank field: an
 * empty address looks like a bug in the app, and "no network" looks like the fact it is.
 *
 * ### Three things this card is careful about
 *
 * **It never wraps.** The address shrinks until it fits on one line. A wrapped URL breaks after
 * the colon and orphans the port on the second line, which is exactly the character someone is
 * squinting at while typing it into another machine.
 *
 * **The whole address is the copy button.** It is the largest thing on the screen and the only
 * thing anyone wants from it, so tapping it copies. The icon stays for discoverability — a tap
 * target with no affordance is a secret.
 *
 * **The scheme is dimmed.** `http://` is identical on every address this app will ever show, so
 * it is the one part of the string carrying no information. The host and port keep full contrast.
 *
 * @param primaryBaseUrl `http://192.168.1.45:8080`, or `null` when nothing is reachable over the LAN
 * @param extraAddresses the ranked addresses after the winner
 * @param advertisedName the mDNS name the system actually registered, suffix and all
 */
@Composable
fun AddressBlock(
    primaryBaseUrl: String?,
    primaryAddress: NetworkAddress?,
    loopbackUrl: String,
    extraAddresses: List<NetworkAddress>,
    boundPort: Int,
    isExpanded: Boolean,
    advertisedName: String?,
    isRunning: Boolean,
    onCopy: (String) -> Unit,
    onShowQr: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.elevationCard),
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.space)
                // Dimmed while stopped. Nothing is listening on these addresses yet, and showing
                // them at full strength invites pasting one into a client that will then refuse
                // to connect for reasons the screen never mentioned.
                .alpha(if (isRunning) 1f else DISABLED_ALPHA),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            if (primaryBaseUrl == null) {
                Text(
                    text = stringResource(R.string.server_no_network),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.server_no_network_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isRunning) {
                                Modifier.clickable { onCopy(primaryBaseUrl) }
                            } else {
                                Modifier
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroAddress(url = primaryBaseUrl, modifier = Modifier.weight(ADDRESS_WEIGHT))
                    // No copy or QR while stopped: both hand the address to something that is
                    // about to fail to reach it.
                    if (isRunning) {
                        IconButton(onClick = { onCopy(primaryBaseUrl) }) {
                            Icon(
                                imageVector = MockIcons.Copy,
                                contentDescription = stringResource(R.string.server_copy),
                            )
                        }
                        IconButton(onClick = onShowQr) {
                            Icon(
                                imageVector = MockIcons.QrCode,
                                contentDescription = stringResource(R.string.server_qr),
                            )
                        }
                    }
                }
                primaryAddress?.let { address ->
                    Text(
                        text = if (isRunning) {
                            stringResource(R.string.server_interface, address.interfaceName)
                        } else {
                            stringResource(R.string.server_interface_stopped, address.interfaceName)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlatDivider()

            AddressRow(
                url = loopbackUrl,
                subtitle = stringResource(R.string.server_loopback_note),
                isRunning = isRunning,
                onCopy = onCopy,
            )

            if (extraAddresses.isNotEmpty()) {
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        if (isExpanded) {
                            stringResource(R.string.server_hide_addresses)
                        } else {
                            pluralStringResource(
                                R.plurals.server_more_addresses,
                                extraAddresses.size,
                                extraAddresses.size,
                            )
                        },
                    )
                }
                if (isExpanded) {
                    extraAddresses.forEach { address ->
                        AddressRow(
                            url = address.toBaseUrl(boundPort),
                            subtitle = when (address.kind) {
                                // The VPN row carries its own explanation: it looks like a
                                // perfectly good address and no LAN client can route to it
                                // (SPEC §13).
                                NetworkAddress.Kind.VPN -> stringResource(R.string.server_vpn_note)
                                else -> address.interfaceName
                            },
                            isRunning = isRunning,
                            onCopy = onCopy,
                        )
                    }
                }
            }

            advertisedName?.let { name ->
                Text(
                    text = stringResource(R.string.server_advertised_as, name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The winning address, as large as fits on one line.
 *
 * `BasicText` rather than `Text` because auto-sizing lives on the foundation one, which is also
 * why the style has to carry the colour Material would otherwise have supplied.
 */
@Composable
private fun HeroAddress(url: String, modifier: Modifier = Modifier) {
    BasicText(
        text = url.dimScheme(MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = Dimens.heroAddressMinTextSize,
            maxFontSize = Dimens.heroAddressMaxTextSize,
        ),
    )
}

/** A secondary address: monospaced, tappable to copy, with what it is underneath. */
@Composable
private fun AddressRow(
    url: String,
    subtitle: String,
    isRunning: Boolean,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // Same rule as the hero address above: nothing to copy until something answers.
                if (isRunning) Modifier.clickable { onCopy(url) } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(ADDRESS_WEIGHT)) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRunning) {
            IconButton(onClick = { onCopy(url) }) {
                Icon(
                    imageVector = MockIcons.Copy,
                    contentDescription = stringResource(R.string.server_copy),
                )
            }
        }
    }
}

/** `http://` in [muted], the host and port at full contrast. */
private fun String.dimScheme(muted: Color): AnnotatedString {
    val separator = "://"
    val schemeEnd = indexOf(separator).takeIf { it >= 0 }?.plus(separator.length) ?: 0
    return buildAnnotatedString {
        if (schemeEnd > 0) {
            withStyle(SpanStyle(color = muted)) { append(substring(0, schemeEnd)) }
        }
        append(substring(schemeEnd))
    }
}

/** The address takes whatever the icon buttons leave. */
private const val ADDRESS_WEIGHT = 1f

@Preview(name = "Address block — on Wi-Fi")
@Composable
internal fun AddressBlockPreview() {
    AppTheme {
        AddressBlock(
            primaryBaseUrl = "http://192.168.1.45:8080",
            primaryAddress = NetworkAddress(
                interfaceName = "wlan0",
                hostAddress = "192.168.1.45",
                kind = NetworkAddress.Kind.WIFI,
            ),
            loopbackUrl = "http://localhost:8080",
            extraAddresses = listOf(
                NetworkAddress(
                    interfaceName = "tun0",
                    hostAddress = "10.0.2.15",
                    kind = NetworkAddress.Kind.VPN,
                ),
            ),
            boundPort = 8080,
            isExpanded = true,
            isRunning = true,
            advertisedName = "MockServer-Pixel-8",
            onCopy = {},
            onShowQr = {},
            onToggleExpanded = {},
        )
    }
}

@Preview(name = "Address block — long hotspot address")
@Composable
internal fun AddressBlockLongAddressPreview() {
    AppTheme {
        AddressBlock(
            primaryBaseUrl = "http://192.168.243.117:52080",
            primaryAddress = NetworkAddress(
                interfaceName = "swlan0",
                hostAddress = "192.168.243.117",
                kind = NetworkAddress.Kind.HOTSPOT,
            ),
            loopbackUrl = "http://localhost:52080",
            extraAddresses = emptyList(),
            boundPort = 52080,
            isExpanded = false,
            isRunning = true,
            advertisedName = null,
            onCopy = {},
            onShowQr = {},
            onToggleExpanded = {},
        )
    }
}

@Preview(name = "Address block — no network")
@Composable
internal fun AddressBlockNoNetworkPreview() {
    AppTheme {
        AddressBlock(
            primaryBaseUrl = null,
            primaryAddress = null,
            loopbackUrl = "http://localhost:8080",
            extraAddresses = emptyList(),
            boundPort = 8080,
            isExpanded = false,
            isRunning = true,
            advertisedName = null,
            onCopy = {},
            onShowQr = {},
            onToggleExpanded = {},
        )
    }
}
