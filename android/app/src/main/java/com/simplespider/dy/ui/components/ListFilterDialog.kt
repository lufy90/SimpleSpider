package com.simplespider.dy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ListFilterButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Icon(Icons.Filled.FilterList, contentDescription = "Filter")
    }
}

@Composable
fun ListFilterDialog(
    title: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClear) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApply) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
fun ChoiceColumn(
    options: List<Pair<String, String?>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    options.forEach { (label, value) ->
        val isSelected = selected == value
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    role = Role.RadioButton,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun TriStateColumn(
    current: Boolean?,
    onSelect: (Boolean?) -> Unit,
) {
    val selected = when (current) {
        null -> "any"
        true -> "yes"
        false -> "no"
    }
    ChoiceColumn(
        options = listOf("Any" to "any", "Yes" to "yes", "No" to "no"),
        selected = selected,
        onSelect = { key ->
            onSelect(
                when (key) {
                    "yes" -> true
                    "no" -> false
                    else -> null
                },
            )
        },
    )
}

@Composable
fun RateChoiceColumn(
    current: Int?,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf("Any" to null) + (0..5).map { it.toString() to it.toString() }
    ChoiceColumn(
        options = options,
        selected = current?.toString(),
        onSelect = { onSelect(it?.toIntOrNull()) },
    )
}

@Composable
fun StatusChoiceColumn(
    current: String?,
    onSelect: (String?) -> Unit,
) {
    ChoiceColumn(
        options = listOf(
            "Any" to null,
            "Ready" to "ready",
            "Waiting" to "waiting",
            "Running" to "running",
            "Error" to "error",
        ),
        selected = current,
        onSelect = onSelect,
    )
}

@Composable
fun RateRangeFields(
    minRate: String,
    maxRate: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = minRate,
            onValueChange = { onMinChange(it.filter { ch -> ch.isDigit() }) },
            label = { Text("Min") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = maxRate,
            onValueChange = { onMaxChange(it.filter { ch -> ch.isDigit() }) },
            label = { Text("Max") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}
