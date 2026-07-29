package com.xteink.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.monetization.AccessState
import com.xteink.companion.monetization.PremiumAction

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectedAccessPrompt(
    action: PremiumAction,
    access: AccessState,
    onWatchAd: () -> Unit,
    onUsePhoneOnly: (() -> Unit)?,
    onGetPro: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    when (action) {
                        PremiumAction.FocusOnDevice -> R.string.access_focus_title
                        PremiumAction.SendPass -> R.string.access_pass_title
                        PremiumAction.PushBooks -> R.string.access_books_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    when (action) {
                        PremiumAction.FocusOnDevice -> R.string.access_focus_body
                        PremiumAction.SendPass -> R.string.access_pass_body
                        PremiumAction.PushBooks -> R.string.access_books_body
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onWatchAd,
                enabled = access.rewardedAdReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    stringResource(
                        if (access.rewardedAdReady) R.string.watch_ad_unlock_day
                        else R.string.preparing_ad,
                    ),
                )
            }
            FilledTonalButton(
                onClick = {
                    onUsePhoneOnly?.invoke()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    stringResource(
                        if (onUsePhoneOnly != null) R.string.start_on_phone
                        else R.string.not_now,
                    ),
                )
            }
            access.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(2.dp))
            ProUpgradeSurface(
                access = access,
                onGetPro = onGetPro,
            )
        }
    }
}

@Composable
fun ProUpgradeSurface(
    access: AccessState,
    onGetPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!access.isPlayDistribution) return
    Surface(
        onClick = onGetPro,
        enabled = access.billingAvailable && !access.isPro,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = when {
                    access.isPro -> stringResource(R.string.pro_active)
                    access.localizedProPrice != null ->
                        stringResource(R.string.get_pro_with_price, access.localizedProPrice)
                    else -> stringResource(R.string.get_pro)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (access.isPro) R.string.pro_active_body else R.string.pro_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
