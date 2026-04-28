package com.workdiary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * An invisible placeholder cell used to pad the beginning of the month calendar grid.
 *
 * When the 1st of a month does not fall on the grid's first column (Monday or Sunday depending
 * on user preference), empty cells are rendered before the first real day cell. This composable
 * provides a same-size transparent placeholder that preserves the grid layout.
 */
@Composable
fun EmptyMonthCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}

@Preview(showBackground = true, name = "EmptyMonthCell")
@Composable
private fun EmptyMonthCellPreview() {
    EmptyMonthCell()
}
