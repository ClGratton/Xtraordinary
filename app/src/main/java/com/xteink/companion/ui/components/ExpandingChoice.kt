package com.xteink.companion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ExpandingChoice(
    val key: String,
    val title: String,
    val body: String,
)

@Composable
fun ExpandingChoiceRow(
    choices: List<ExpandingChoice>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    optionHeight: Dp,
    modifier: Modifier = Modifier,
    selectedContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    restingContainer: Color = MaterialTheme.colorScheme.surfaceContainer,
    restingContent: Color = MaterialTheme.colorScheme.onSurface,
    actionContent: @Composable ColumnScope.(String) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        choices.forEach { choice ->
            val selected = choice.key == selectedKey
            val weight = animateFloatAsState(
                targetValue = if (selected) 1.35f else 0.75f,
                label = "${choice.key} option width",
            )
            ExpandingChoiceOption(
                choice = choice,
                selected = selected,
                onSelect = { onSelect(choice.key) },
                optionHeight = optionHeight,
                selectedContainer = selectedContainer,
                selectedContent = selectedContent,
                restingContainer = restingContainer,
                restingContent = restingContent,
                modifier = Modifier.weight(weight.value),
                actionContent = actionContent,
            )
        }
    }
}

@Composable
private fun ExpandingChoiceOption(
    choice: ExpandingChoice,
    selected: Boolean,
    onSelect: () -> Unit,
    optionHeight: Dp,
    selectedContainer: Color,
    selectedContent: Color,
    restingContainer: Color,
    restingContent: Color,
    modifier: Modifier = Modifier,
    actionContent: @Composable ColumnScope.(String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onSelect()
        },
        modifier = modifier.height(optionHeight),
        color = if (selected) selectedContainer else restingContainer,
        contentColor = if (selected) selectedContent else restingContent,
        shape = MaterialTheme.shapes.large,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(choice.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                text = choice.body,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column {
                    actionContent(choice.key)
                }
            }
        }
    }
}
