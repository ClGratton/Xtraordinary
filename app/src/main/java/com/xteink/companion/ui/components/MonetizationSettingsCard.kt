package com.xteink.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.monetization.MonetizationRuntimeState
import com.xteink.companion.monetization.PurchasePhase

@Composable
fun MonetizationSettingsCard(
    state: MonetizationRuntimeState,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onOpenCommunitySource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_ad_free), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ad_free_product_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = when {
                        state.isCommunity -> stringResource(R.string.ad_free_community_active)
                        state.isPurchased -> stringResource(R.string.ad_free_play_active)
                        else -> stringResource(R.string.ad_free_value)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!state.isCommunity && !state.isPurchased) {
                    Text(
                        stringResource(R.string.ad_free_benefits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val price = state.product?.formattedPrice
                    if (price != null) {
                        Text(stringResource(R.string.ad_free_price, price), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onBuy,
                        enabled = state.purchasePhase == PurchasePhase.Ready && state.product != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(
                            when (state.purchasePhase) {
                                PurchasePhase.Purchasing -> stringResource(R.string.ad_free_opening_play)
                                PurchasePhase.Pending -> stringResource(R.string.ad_free_pending)
                                PurchasePhase.Verifying -> stringResource(R.string.ad_free_verifying)
                                else -> stringResource(R.string.ad_free_buy)
                            },
                        )
                    }
                }

                state.message?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.testMode) {
                    Text(
                        stringResource(R.string.ad_free_test_configuration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactActions = maxWidth < 320.dp
                    if (compactActions) {
                        Column {
                            if (!state.isCommunity) {
                                TextButton(
                                    onClick = onRestore,
                                    enabled = state.purchasePhase !in setOf(
                                        PurchasePhase.Purchasing,
                                        PurchasePhase.Pending,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.ad_free_restore))
                                }
                            }
                            if (state.privacyOptionsRequired) {
                                TextButton(
                                    onClick = onPrivacyOptions,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.ad_privacy))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!state.isCommunity) {
                                TextButton(
                                    onClick = onRestore,
                                    enabled = state.purchasePhase !in setOf(
                                        PurchasePhase.Purchasing,
                                        PurchasePhase.Pending,
                                    ),
                                ) {
                                    Text(stringResource(R.string.ad_free_restore))
                                }
                            }
                            if (state.privacyOptionsRequired) {
                                TextButton(onClick = onPrivacyOptions) { Text(stringResource(R.string.ad_privacy)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onOpenCommunitySource) {
                    Text(
                        stringResource(
                            if (state.isCommunity) R.string.ad_free_view_source else R.string.ad_free_community_source,
                        ),
                    )
                }
            }
        }
    }
}
