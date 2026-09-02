package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.ui.R

/**
 * The persistent "chaos is on" banner (SPEC §6.1).
 *
 * Non-dismissible while a profile with failure modes is active, and it names the profile. Chaos
 * that is on and invisible is the fastest way to destroy trust in a mock server: an injected 503
 * is indistinguishable from a broken rule, and the hour spent finding that out is an hour of
 * believing the tool is lying.
 */
@Composable
fun ChaosBanner(
    profileName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                RoundedCornerShape(Dimens.cornerSmall),
            )
            .padding(Dimens.spaceMedium),
    ) {
        Text(
            text = stringResource(R.string.chaos_banner_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = stringResource(R.string.chaos_banner_body, profileName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * The permanent note shown while the API key is off (SPEC §8.6).
 *
 * Not dismissible, and phrased as a fact rather than a warning. The server really is open to
 * everything on the network; softening that, or letting it be dismissed, would be exactly the
 * security theatre the spec rules out.
 */
@Composable
fun OpenAccessNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.server_open_access_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
internal fun ChaosBannerPreview() {
    AppTheme { ChaosBanner(profileName = "Flaky backend") }
}
