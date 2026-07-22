package com.xteink.companion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xteink.companion.R

private data class ToolSpec(
    val glyph: String,
    val title: String,
    val body: String,
    val available: Boolean = false,
)

@Composable
fun ToolsHubContent(
    passCount: Int,
    onOpenPasses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        ToolSpec("♪", stringResource(R.string.tool_now_playing), stringResource(R.string.tool_now_playing_body)),
        ToolSpec("↗", stringResource(R.string.tool_navigation), stringResource(R.string.tool_navigation_body)),
        ToolSpec("✓", stringResource(R.string.tool_lists), stringResource(R.string.tool_lists_body)),
        ToolSpec("31", stringResource(R.string.tool_calendar), stringResource(R.string.tool_calendar_body)),
        ToolSpec("T", stringResource(R.string.tool_shared_text), stringResource(R.string.tool_shared_text_body)),
        ToolSpec("◎", stringResource(R.string.tool_phone_status), stringResource(R.string.tool_phone_status_body)),
        ToolSpec("●", stringResource(R.string.tool_camera), stringResource(R.string.tool_camera_body)),
        ToolSpec("✦", stringResource(R.string.tool_ai), stringResource(R.string.tool_ai_body)),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.tools_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToolCard(
            spec = ToolSpec(
                glyph = "✈",
                title = stringResource(R.string.tool_passes),
                body = stringResource(R.string.tool_passes_body),
                available = true,
            ),
            supporting = pluralStringResource(R.plurals.passes_count, passCount, passCount),
            onClick = onOpenPasses,
            modifier = Modifier.fillMaxWidth(),
        )
        tools.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { spec ->
                    ToolCard(
                        spec = spec,
                        supporting = stringResource(R.string.planned),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    spec: ToolSpec,
    supporting: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .heightIn(min = 126.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    }
                } else {
                    Modifier
                },
            ),
        color = if (spec.available) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (spec.available) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(spec.glyph, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(spec.title, style = MaterialTheme.typography.titleMedium)
            Text(
                spec.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
