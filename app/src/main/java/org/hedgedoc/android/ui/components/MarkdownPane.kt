package org.hedgedoc.android.ui.components

import android.text.Spanned
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import io.noties.markwon.linkify.LinkifyPlugin
import okhttp3.OkHttpClient
import org.hedgedoc.android.ui.theme.LocalScient

@Composable
fun MarkdownPane(
    markdown: String,
    baseUrl: String,
    http: OkHttpClient,
    modifier: Modifier = Modifier,
    textColor: Int = LocalScient.current.paperInk.toArgb(),
    onToggleTask: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val pal = LocalScient.current
    val toggle = rememberUpdatedState(onToggleTask)
    val markwon = remember(baseUrl, http) {
        val loader = ImageLoader.Builder(context)
            .okHttpClient(http)
            .build()
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context, loader))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageDestinationProcessor(
                        ImageDestinationProcessorRelativeToAbsolute.create(baseUrl),
                    )
                }
            })
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 16.5f
                setLineSpacing(0f, 1.28f)
                setLinkTextColor(pal.textSoft.toArgb())
                setOnTouchListener { view, event ->
                    val handler = toggle.value
                    if (handler == null || event.actionMasked != MotionEvent.ACTION_UP) {
                        false
                    } else {
                        val index = taskIndexAt(view as TextView, event)
                        if (index < 0) {
                            false
                        } else {
                            view.performClick()
                            handler(index)
                            true
                        }
                    }
                }
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            markwon.setMarkdown(view, markdown.ifBlank { "_This note is empty._" })
        },
    )
}

/**
 * Which task list checkbox, if any, sits under this touch.
 *
 * Only the leading margin counts as a hit. The margin is the indent Markwon reserves for the box
 * itself, so tapping the text of a task item still selects links instead of toggling the note.
 */
private fun taskIndexAt(view: TextView, event: MotionEvent): Int {
    val spanned = view.text as? Spanned ?: return -1
    val layout = view.layout ?: return -1
    val x = event.x - view.totalPaddingLeft + view.scrollX
    val y = event.y - view.totalPaddingTop + view.scrollY
    if (y < 0) return -1
    val line = layout.getLineForVertical(y.toInt())
    val offset = layout.getOffsetForHorizontal(line, x)
    val hit = spanned.getSpans(offset, offset, TaskListSpan::class.java).firstOrNull() ?: return -1
    if (x > layout.getLineLeft(line) + hit.getLeadingMargin(true)) return -1
    val all = spanned.getSpans(0, spanned.length, TaskListSpan::class.java)
        .sortedBy { spanned.getSpanStart(it) }
    return all.indexOf(hit)
}
