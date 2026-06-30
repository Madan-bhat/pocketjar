package com.mchost.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StepProgressIndicator(currentStep: Int, labels: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        labels.forEachIndexed { index, label ->
            CloudMcStepDot(
                index = index,
                label = label,
                active = index == currentStep,
                done = index < currentStep,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
