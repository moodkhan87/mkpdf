package com.mkpdf.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/** Continuous vertical list of pages. Pinch zooms the whole list; drag up/down scrolls; drag sideways pans when zoomed. */
@Composable
fun PdfPages(doc: Document, ui: UiState, vm: EditorViewModel, listState: LazyListState, onField: (FormField) -> Unit) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(doc.handle) { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val transform = rememberTransformableState { factor, _, _ -> zoom = (zoom * factor).coerceIn(1f, 4f) }
    val active = ui.hits.getOrNull(ui.selectedHit)
    val margin = with(density) { 8.dp.toPx() }

    Box(Modifier.fillMaxSize().background(Color(0xFFE3E8E6)).onSizeChanged { viewport = it }
        .transformable(transform, canPan = { false })) {
        if (viewport.width > 0) {
            val listWidthPx = viewport.width * zoom
            val pageWidthPx = listWidthPx - 2 * margin
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                LazyColumn(
                    Modifier.width(with(density) { listWidthPx.toDp() }).fillMaxHeight(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = doc.pages.size, key = { "${doc.handle}:$it" }) { index ->
                        PdfPageItem(doc, index, pageWidthPx, ui.hits, active, ui.fields, vm, onField) {
                            zoom = if (zoom > 1.1f) 1f else 2f
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    doc: Document, index: Int, widthPx: Float, hits: List<Hit>, active: Hit?, fields: List<FormField>,
    vm: EditorViewModel, onField: (FormField) -> Unit, onToggleZoom: () -> Unit
) {
    val page = doc.pages[index]
    val density = LocalDensity.current
    val heightPx = widthPx * page.height / page.width
    var bitmap by remember(doc.handle, doc.generation, index) { mutableStateOf<Bitmap?>(null) }
    var error by remember(doc.handle, doc.generation, index) { mutableStateOf<String?>(null) }
    // The native raster limit is about 8 million pixels, so cap the width for tall pages.
    val maxWidth = sqrt(8_000_000.0 * page.width / page.height).toInt()
    val desired = widthPx.toInt().coerceIn(64, maxOf(64, minOf(4096, maxWidth)))
    LaunchedEffect(doc.handle, doc.generation, index, desired) {
        if (bitmap != null) delay(180) // keep the old bitmap scaled, refresh detail after zoom settles
        try { bitmap = vm.render(doc, index, desired); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    Box(
        Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .background(Color.White)
            .pointerInput(doc.handle, doc.generation, index, fields) {
                detectTapGestures(onDoubleTap = { onToggleZoom() }, onTap = { pos ->
                    val x = pos.x / size.width * page.width
                    val y = pos.y / size.height * page.height
                    fields.firstOrNull { f ->
                        f.widgets.any { w ->
                            !w.hidden && w.page == index && x >= w.rect.left && x <= w.rect.right && y >= w.rect.top && y <= w.rect.bottom
                        }
                    }?.let(onField)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { Image(it.asImageBitmap(), "PDF page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / page.width
            val sy = size.height / page.height
            fun paint(r: PdfRect, color: Color) {
                drawRect(color, Offset(r.left * sx, r.top * sy), Size((r.right - r.left) * sx, (r.bottom - r.top) * sy))
            }
            hits.filter { it.page == index }.forEach { h ->
                h.rects.forEach { paint(it, if (h == active) Color(0x88FF8800) else Color(0x66FFE500)) }
            }
            fields.flatMap { it.widgets }.filter { it.page == index && !it.hidden }.forEach { paint(it.rect, Color(0x18008877)) }
        }
        if (bitmap == null || error != null) Text(error ?: "Rendering…", color = if (error != null) Color.Red else Color.DarkGray)
    }
}