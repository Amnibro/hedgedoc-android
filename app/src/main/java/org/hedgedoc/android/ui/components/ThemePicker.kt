package org.hedgedoc.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hedgedoc.android.ui.theme.ScientPalettes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScientPalettes.All.forEach { pal ->
            FilterChip(
                selected = pal.id == selectedId,
                onClick = { onSelect(pal.id) },
                label = { Text(pal.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = pal.accent,
                    selectedLabelColor = pal.accentInk,
                    containerColor = pal.panel,
                    labelColor = pal.text,
                ),
            )
        }
    }
}
