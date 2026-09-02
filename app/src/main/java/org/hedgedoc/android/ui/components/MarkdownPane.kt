package org.hedgedoc.android.ui.components

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import io.noties.markwon.linkify.LinkifyPlugin
import okhttp3.OkHttpClient
import org.hedgedoc.android.ui.theme.Ink
import org.hedgedoc.android.ui.theme.InkMute

@Composable
fun MarkdownPane(
    markdown: String,
    baseUrl: String,
    http: OkHttpClient,
    modifier: Modifier = Modifier,
    textColor: Int = Ink.toArgb(),
) {
    val context = LocalContext.current
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
                setLinkTextColor(InkMute.toArgb())
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            markwon.setMarkdown(view, markdown.ifBlank { "_This note is empty._" })
        },
    )
}
