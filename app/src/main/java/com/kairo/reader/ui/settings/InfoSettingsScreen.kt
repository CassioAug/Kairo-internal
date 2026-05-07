@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/kairoapp"
private const val GITHUB_URL = "https://github.com/Steadyx/Kairo"

@Composable
fun InfoSettingsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(
        title = stringResource(R.string.info_settings_title),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                title = stringResource(R.string.info_buy_me_a_coffee_title),
                subtitle = stringResource(R.string.info_buy_me_a_coffee_subtitle),
                icon = Icons.Default.Favorite,
                onClick = { uriHandler.openUri(BUY_ME_A_COFFEE_URL) },
            )
            SettingsNavRow(
                title = stringResource(R.string.info_contribute_title),
                subtitle = stringResource(R.string.info_contribute_subtitle),
                icon = Icons.Default.Code,
                onClick = { uriHandler.openUri(GITHUB_URL) },
            )
        }
    }
}
