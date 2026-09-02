package org.hedgedoc.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hedgedoc.android.data.HistoryNote
import org.hedgedoc.android.ui.theme.Blot
import org.hedgedoc.android.ui.theme.Ink
import org.hedgedoc.android.ui.theme.InkMute
import org.hedgedoc.android.ui.theme.Label
import org.hedgedoc.android.ui.theme.Spine
import org.hedgedoc.android.ui.theme.Stake
import org.hedgedoc.android.ui.theme.Verdigris
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PlantLabelShape = GenericShape { size, _ ->
    val fold = size.minDimension * 0.14f
    moveTo(0f, 0f)
    lineTo(size.width - fold, 0f)
    lineTo(size.width, fold)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun QuillGutter(
    pinned: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (pinned) Spine else Stake
    val ticks = if (pinned) 12 else 8
    Canvas(modifier = modifier) {
        val step = size.height / (ticks + 1)
        val lean = if (pinned) 11.dp.toPx() else 8.dp.toPx()
        for (i in 1..ticks) {
            val y = step * i
            drawLine(
                color = color,
                start = Offset(1.5.dp.toPx(), y + 2.dp.toPx()),
                end = Offset(lean, y - 5.dp.toPx()),
                strokeWidth = if (pinned) 3.4f else 2.3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun NoteRow(
    note: HistoryNote,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stamp = if (note.time > 0) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(note.time))
    } else {
        ""
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PlantLabelShape)
            .background(Label)
            .clickable(onClick = onClick)
            .height(96.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuillGutter(
            pinned = note.pinned,
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .padding(vertical = 14.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stamp.isNotBlank()) {
                    Text(stamp, style = MaterialTheme.typography.labelSmall, color = InkMute)
                }
                if (note.pinned) {
                    Text("pinned", style = MaterialTheme.typography.labelSmall, color = Spine)
                }
                note.tags.take(3).forEach { tag ->
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = Verdigris)
                }
            }
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Note actions", tint = InkMute)
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Blot.copy(alpha = 0.18f))
            .padding(12.dp),
    ) {
        Text(message, color = Blot, style = MaterialTheme.typography.bodyMedium)
    }
}
