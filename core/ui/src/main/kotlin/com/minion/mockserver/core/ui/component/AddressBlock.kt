package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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
            modifier = Modifier.padding(Dimens.space),
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
                Text(
                    text = primaryBaseUrl,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Dimens.heroAddressTextSize,
                    ),
                )
                primaryAddress?.let { address ->
                    Text(
                        text = stringResource(R.string.server_interface, address.interfaceName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                    TextButton(onClick = { onCopy(primaryBaseUrl) }) {
                        Text(stringResource(R.string.server_copy))
                    }
                    TextButton(onClick = onShowQr) {
                        Text(stringResource(R.string.server_qr))
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = loopbackUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                TextButton(onClick = { onCopy(loopbackUrl) }) {
                    Text(stringResource(R.string.server_copy))
                }
            }

            if (extraAddresses.isNotEmpty()) {
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        if (isExpanded) {
                            stringResource(R.string.server_hide_addresses)
                        } else {
                            stringResource(R.string.server_more_addresses, extraAddresses.size)
                        },
                    )
                }
                if (isExpanded) {
                    extraAddresses.forEach { address ->
                        AddressRow(address = address, port = boundPort, onCopy = onCopy)
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

/** One of the also-ran addresses, with the interface it belongs to. */
@Composable
private fun AddressRow(
    address: NetworkAddress,
    port: Int,
    onCopy: (String) -> Unit,
) {
    val url = address.toBaseUrl(port)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = when (address.kind) {
                    // The VPN row carries its own explanation: it looks like a perfectly good
                    // address and no LAN client can route to it (SPEC §13).
                    NetworkAddress.Kind.VPN -> stringResource(R.string.server_vpn_note)
                    else -> address.interfaceName
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onCopy(url) }) {
            Text(stringResource(R.string.server_copy))
        }
    }
}

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
            advertisedName = "MockServer-Pixel-8",
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
            advertisedName = null,
            onCopy = {},
            onShowQr = {},
            onToggleExpanded = {},
        )
    }
}
