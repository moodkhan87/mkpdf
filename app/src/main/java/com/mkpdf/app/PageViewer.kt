package com.mkpdf.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.minOf
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f

/**
 * Continuous vertical list of pages. Pinch zooms around the fingers (50% to 400%), one finger scrolls up/down
 * and, when zoomed in, also pans sideways. Page images are re-rendered only after the zoom has settled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPages(doc: Document, ui: UiState, vm: EditorViewModel, listState: LazyListState, onField: (FormField) -> Unit) {
    val currentDoc by rememberUpdatedState(doc)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(doc.handle) { mutableFloatStateOf(1f) }
    var renderZoom by remember(doc.handle) { mutableFloatStateOf(1f) }
    var panX by remember(doc.handle) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val active = ui.hits.getOrNull(ui.selectedHit)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Re-render the page images only after the pinch has settled.
    LaunchedEffect(zoom) { delay(250); renderZoom = zoom }

    fun clampPan(x: Float, z: Float): Float = if (z <= 1f) 0f else x.coerceIn(viewport.width * (1f - z), 0f)
    fun pageHeightPx(i: Int, z: Float): Float {
        val p = currentDoc.pages[i]
        return (viewport.width * z - 2 * gap) * p.height / p.width
    }
    /** Distance from the top of the document to the top of the screen, measured with page sizes at zoom [z]. */
    fun absScroll(z: Float): Float {
        val idx = listState.firstVisibleItemIndex.coerceAtMost((currentDoc.pages.size - 1).coerceAtLeast(0))
        var s = 0f
        for (i in 0 until idx) s += pageHeightPx(i, z) + gap
        return s + listState.firstVisibleItemScrollOffset
    }
    /** Change the zoom and keep the document point under (cx, cy) where it was. */
    fun zoomBy(requested: Float, cx: Float, cy: Float) {
        val nz = requested.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (nz == zoom || viewport.width == 0) return
        val r = nz / zoom
        val oldLeft = if (zoom < 1f) viewport.width * (1f - zoom) / 2f else panX
        val newPan = cx - (cx - oldLeft) * r
        val targetAbs = (absScroll(zoom) + cy) * r - cy
        val afterRelayout = absScroll(nz)
        zoom = nz
        panX = clampPan(newPan, nz)
        listState.dispatchRawDelta(targetAbs - afterRelayout)
    }

    Box(
        Modifier.fillMaxSize()
            .background(if (dark) Color(0xFF2B2F2E) else Color(0xFFE3E8E6))
            .onSizeChanged { viewport = it }
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Watch touches before the list does; only two-finger gestures are taken over.
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.count { it.pressed }
                        if (down >= 2) {
                            val zc = event.calculateZoom()
                            val c = event.calculateCentroid(useCurrent = true)
                            val pan = event.calculatePan()
                            if (zc != 1f || pan != Offset.Zero) {
                                zoomBy(zoom * zc, c.x, c.y)
                                if (zoom > 1f) panX = clampPan(panX + pan.x, zoom)
                                listState.dispatchRawDelta(-pan.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (down == 1 && zoom > 1.01f) {
                            val dx = event.calculatePan().x
                            if (dx != 0f) panX = clampPan(panX + dx, zoom)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (viewport.width > 0) {
            val vw = viewport.width
            val vh = viewport.height
            val contentW = (vw * zoom).roundToInt().coerceAtLeast(1)
            val pageW = (vw * zoom - 2 * gap).coerceAtLeast(1f)
            val renderW = (vw * renderZoom - 2 * gap).coerceAtLeast(1f)
            Box(Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(contentW, vh))
                layout(vw, vh) {
                    val x = if (zoom <= 1f) (vw - contentW) / 2 else clampPan(panX, zoom).roundToInt()
                    placeable.place(x, 0)
                }
            }) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = doc.pages.size, key = { "${doc.handle}:$it" }) { index ->
                        PdfPageItem(doc, index, pageW, renderW, ui.hits, active, ui.fields, vm, onField) {
                            zoomBy(if (zoom > 1.1f) 1f else 2f, vw / 2f, vh / 2f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    doc: Document, index: Int, widthPx: Float, renderWidthPx: Float, hits: List<Hit>, active: Hit?, fields: List<FormField>,
    vm: EditorViewModel, onField: (FormField) -> Unit, onToggleZoom: () -> Unit
) {
    val page = doc.pages[index]
    val density = LocalDensity.current
    val heightPx = widthPx * page.height / page.width
    var bitmap by remember(doc.handle, doc.generation, index) { mutableStateOf<Bitmap?>(null) }
    var error by remember(doc.handle, doc.generation, index) { mutableStateOf<String?>(null) }
    // The native raster limit is about 8 million pixels, so cap the width for tall pages.
    val maxWidth = sqrt(8_000_000.0 * page.width / page.height).toInt()
    val desired = renderWidthPx.toInt().coerceIn(64, maxOf(64, minOf(4096, maxWidth)))
    LaunchedEffect(doc.handle, doc.generation, index, desired) {
        if (bitmap != null) delay(120) // keep the old bitmap scaled, refresh detail afterwards
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