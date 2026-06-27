package com.mchost.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary

@Composable
fun StepProgressIndicator(currentStep: Int, labels: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == currentStep
            val done = index < currentStep
            ColumnStepDot(
                label = label,
                active = active,
                done = done,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColumnStepDot(label: String, active: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        active -> Accent
        done -> Accent.copy(alpha = 0.55f)
        else -> TextSecondary.copy(alpha = 0.35f)
    }
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .height(28.dp)
                .clip(CircleShape)
                .background(if (active) color else Surface)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    done -> "✓"
                    else -> "${label.take(1)}"
                },
                color = if (active) androidx.compose.ui.graphics.Color.Black else color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Text(
            label,
            color = if (active) Accent else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
