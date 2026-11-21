package com.mkpdf.app

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f

/** Where the dragging finger currently points, in viewport coordinates. */
private data class SelectionDrag(val side: Int, val viewportY: Float, val page: Int, val x: Float, val y: Float)

/**
 * Continuous vertical list of pages. Pinch zooms around the fingers (50% to 400%), one finger scrolls up/down
 * and, when zoomed in, also pans sideways. Page images are re-rendered only after the zoom has settled.
 * A scroll handle with a page bubble appears while touching and fades out afterwards.
 * Dragging a selection handle near the top/bottom edge auto-scrolls and extends the selection across pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPages(doc: Document, ui: UiState, vm: EditorViewModel, listState: LazyListState, onField: (FormField) -> Unit) {
    val currentDoc by rememberUpdatedState(doc)
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(doc.handle) { mutableFloatStateOf(1f) }
    var renderZoom by remember(doc.handle) { mutableFloatStateOf(1f) }
    var panX by remember(doc.handle) { mutableFloatStateOf(0f) }
    var touchTick by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var showHandle by remember { mutableStateOf(false) }
    var pendingAnchor by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) } // page index, fraction down the page, screen y
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val active = ui.hits.getOrNull(ui.selectedHit)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrolling = listState.isScrollInProgress

    // ---- Cross-page selection drag: shared between all page items. ----
    var selDrag by remember(doc.handle) { mutableStateOf<SelectionDrag?>(null) }
    var selAnchorPage by remember(doc.handle) { mutableIntStateOf(0) }
    var selAnchorGlyph by remember(doc.handle) { mutableIntStateOf(0) }
    // True ONLY while a finger is physically down on a handle. The auto-scroll loop must never
    // run without it, otherwise ordinary scrolling would keep extending the selection.
    var fingerDown by remember(doc.handle) { mutableStateOf(false) }
    val latestList by rememberUpdatedState(listState)
    val latestVm by rememberUpdatedState(vm)
    val latestViewport by rememberUpdatedState(viewport)
    val latestZoom by rememberUpdatedState(zoom)
    val latestPan by rememberUpdatedState(panX)
    val latestGap by rememberUpdatedState(gap)
    val latestDoc by rememberUpdatedState(doc)

    // Safety net: any new touch anywhere cancels a stale selection drag.
    fun endSelectionDrag() { fingerDown = false; selDrag = null }

    // Re-render the page images only after the pinch has settled.
    LaunchedEffect(zoom) { delay(250); renderZoom = zoom }
    // Show the scroll handle on touch or scroll; hide it two seconds after the last activity.
    LaunchedEffect(touchTick, scrolling, dragging) {
        showHandle = true
        if (!scrolling && !dragging) { delay(2000); showHandle = false }
    }
    // A new document must never inherit a running drag.
    LaunchedEffect(doc.handle, doc.generation) { endSelectionDrag() }

    fun clampPan(x: Float, z: Float): Float = if (z <= 1f) 0f else x.coerceIn(viewport.width * (1f - z), 0f)
    fun pageHeightPx(i: Int, z: Float): Float {
        val p = currentDoc.pages[i]
        return (viewport.width * z - 2 * gap) * p.height / p.width
    }
    /** Change the zoom and keep the document point under (cx, cy) where it was. */
    fun zoomBy(requested: Float, cx: Float, cy: Float) {
        val nz = requested.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (nz == zoom || viewport.width == 0) return
        val r = nz / zoom
        val oldLeft = if (zoom < 1f) viewport.width * (1f - zoom) / 2f else panX
        val newPan = cx - (cx - oldLeft) * r
        // Remember which page, and which point on it, is under the fingers; the list corrects itself after it re-measures.
        val topOffset = -listState.layoutInfo.viewportStartOffset
        val anchor = listState.layoutInfo.visibleItemsInfo
            .filter { it.index < currentDoc.pages.size && it.size > 0 }
            .minByOrNull {
                val top = (it.offset + topOffset).toFloat()
                val bottom = top + it.size
                if (cy < top) top - cy else if (cy > bottom) cy - bottom else 0f
            }
        if (anchor != null) {
            val top = (anchor.offset + topOffset).toFloat()
            pendingAnchor = Triple(anchor.index, ((cy - top) / anchor.size).coerceIn(0f, 1f), cy)
        }
        zoom = nz
        panX = clampPan(newPan, nz)
    }
    // After a zoom change: wait until the list has measured the anchor page at the new size, then correct once.
    LaunchedEffect(pendingAnchor, zoom) {
        val a = pendingAnchor ?: return@LaunchedEffect
        val expected = pageHeightPx(a.first, zoom)
        val item = withTimeoutOrNull(400) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == a.first } }
                .first { it != null && abs(it.size - expected) < 3f }
        } ?: return@LaunchedEffect
        val top = (item.offset - listState.layoutInfo.viewportStartOffset).toFloat()
        val wanted = a.third - a.second * item.size
        val delta = top - wanted
        if (abs(delta) > 0.5f) listState.dispatchRawDelta(delta)
    }

    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val best = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
            best.coerceIn(0, (currentDoc.pages.size - 1).coerceAtLeast(0))
        }
    }
    val progress by remember {
        derivedStateOf {
            val n = currentDoc.pages.size
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || n < 2) 0f
            else if (!listState.canScrollBackward) 0f
            else if (!listState.canScrollForward) 1f
            else {
                val frac = ((gap - first.offset) / first.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                ((first.index + frac) / (n - 1)).coerceIn(0f, 1f)
            }
        }
    }

    // While a handle is HELD near an edge: scroll the list and keep extending the selection.
    // Runs only while fingerDown is true, so lifting the finger always stops it.
    LaunchedEffect(fingerDown) {
        if (!fingerDown) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (fingerDown) {
            val d = selDrag
            if (d == null) { fingerDown = false; break }
            val vh = latestViewport.height.toFloat()
            val speed = when {
                vh <= 0f -> 0f
                d.viewportY < edge -> -((edge - d.viewportY) / edge) * 26f
                d.viewportY > vh - edge -> ((d.viewportY - (vh - edge)) / edge) * 26f
                else -> 0f
            }
            if (speed != 0f) latestList.dispatchRawDelta(-speed)
            // Resolve the finger against the CURRENT layout, so it follows the page under it while scrolling.
            val ls = latestList
            val z = latestZoom
            val vw = latestViewport.width.toFloat()
            val contentLeft = if (z <= 1f) (vw - vw * z) / 2f else clampPan(latestPan, z)
            val pageWidth = (vw * z - 2 * latestGap).coerceAtLeast(1f)
            val topOffset = -ls.layoutInfo.viewportStartOffset
            val hit = ls.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                info.index < latestDoc.pages.size && info.size > 0 &&
                    d.viewportY >= info.offset + topOffset && d.viewportY < info.offset + topOffset + info.size
            }
            if (hit != null) {
                val p = latestDoc.pages[hit.index]
                val sy = hit.size / p.height
                val px = ((d.x - contentLeft - latestGap) / (pageWidth / p.width)).coerceIn(0f, p.width)
                val py = ((d.viewportY - (hit.offset + topOffset)) / sy).coerceIn(0f, p.height)
                latestVm.extendSelection(selAnchorPage, selAnchorGlyph, hit.index, px, py)
            }
            delay(40)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(if (dark) Color(0xFF2B2F2E) else Color(0xFFE3E8E6))
            .onSizeChanged { viewport = it }
            .clipToBounds()
            // Keyed by the document: a new document gets fresh gesture state, not the previous one's.
            .pointerInput(doc.handle) {
                awaitEachGesture {
                    // Watch touches before the list does; only two-finger gestures are taken over.
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touchTick++
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.count { it.pressed }
                        if (down >= 2) {
                            // A second finger means pinch/scroll, never a handle drag.
                            endSelectionDrag()
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
                    // The gesture ended: no finger is on a handle anymore.
                    endSelectionDrag()
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
                        PdfPageItem(
                            doc, index, pageW, renderW, ui.hits, active, ui.fields, ui.links, ui.selection, vm, onField,
                            { zoomBy(if (zoom > 1.1f) 1f else 2f, vw / 2f, vh / 2f) },
                            { fingerDown },
                            { side, anchorPage, anchorGlyph, localX, localY, pg ->
                                selAnchorPage = anchorPage
                                selAnchorGlyph = anchorGlyph
                                val left = if (zoom <= 1f) (vw - vw * zoom) / 2f else clampPan(panX, zoom)
                                val topOffset = -listState.layoutInfo.viewportStartOffset
                                val itemTop = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == pg }?.let { it.offset + topOffset } ?: 0
                                selDrag = SelectionDrag(side, itemTop + localY, pg, left + gap + localX, 0f)
                                fingerDown = true
                            },
                            { localX, localY, pg ->
                                if (fingerDown) {
                                    val left = if (zoom <= 1f) (vw - vw * zoom) / 2f else clampPan(panX, zoom)
                                    val topOffset = -listState.layoutInfo.viewportStartOffset
                                    val itemTop = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == pg }?.let { it.offset + topOffset } ?: 0
                                    selDrag = SelectionDrag(selDrag?.side ?: 0, itemTop + localY, pg, left + gap + localX, 0f)
                                }
                            },
                            { endSelectionDrag() }
                        )
                    }
                }
            }
        }

        // Scroll handle with a page bubble, like Google Drive's.
        val count = doc.pages.size
        val handleAlpha by animateFloatAsState(if (showHandle && count > 1) 1f else 0f, label = "scrollHandle")
        if (viewport.height > 0 && handleAlpha > 0.01f) {
            val thumbH = with(density) { 72.dp.toPx() }
            val track = (viewport.height - thumbH).coerceAtLeast(1f)
            val shown = if (dragging) dragFraction else progress
            val bubblePage = if (dragging) (dragFraction * (count - 1)).roundToInt() else currentPage
            Row(
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, (shown * track).roundToInt()) }
                    .graphicsLayer { alpha = handleAlpha }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dragging || scrolling) {
                    Surface(color = Color(0xE6303134), shape = RoundedCornerShape(20.dp)) {
                        Text("${bubblePage + 1} / $count", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    color = Color(0xE6303134),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(36.dp, 72.dp).pointerInput(track, count) {
                        detectVerticalDragGestures(
                            onDragStart = { endSelectionDrag(); dragFraction = progress; dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / track).coerceIn(0f, 1f)
                                val target = (dragFraction * (count - 1)).roundToInt()
                                scope.launch { listState.scrollToItem(target) }
                            }
                        )
                    }
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(MkIcons.ArrowUp, null, tint = Color.White)
                        Icon(MkIcons.ArrowDown, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PdfPageItem(
    doc: Document, index: Int, widthPx: Float, renderWidthPx: Float, hits: List<Hit>, active: Hit?, fields: List<FormField>,
    links: List<PdfLink>, selection: List<TextSelection>, vm: EditorViewModel, onField: (FormField) -> Unit,
    onToggleZoom: () -> Unit,
    isDraggingSelection: () -> Boolean,
    onSelectionDragStart: (side: Int, anchorPage: Int, anchorGlyph: Int, localX: Float, localY: Float, page: Int) -> Unit,
    onSelectionDrag: (localX: Float, localY: Float, page: Int) -> Unit,
    onSelectionDragEnd: () -> Unit
) {
    val page = doc.pages[index]
    val density = LocalDensity.current
    val heightPx = widthPx * page.height / page.width
    var shown by remember(doc.handle, index) { mutableStateOf<Pair<Bitmap, Int>?>(null) } // picture + page rotation when it was drawn
    var error by remember(doc.handle, doc.generation, index) { mutableStateOf<String?>(null) }
    // The native raster limit is about 8 million pixels, so cap the width for tall pages.
    val maxWidth = sqrt(8_000_000.0 * page.width / page.height).toInt()
    val desired = renderWidthPx.toInt().coerceIn(64, maxOf(64, minOf(4096, maxWidth)))
    LaunchedEffect(doc.handle, doc.generation, index, desired) {
        if (shown != null) delay(40) // keep the old picture on screen, replace it when the new one is ready
        try { shown = vm.render(doc, index, desired) to page.rotation; error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }

    // Typing directly inside a text field on the page.
    var editing by remember(doc.handle, doc.generation) { mutableStateOf<Pair<FormField, Widget>?>(null) }
    var editText by remember { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // This page's slice of the (possibly multi-page) selection.
    val sel = selection.firstOrNull { it.page == index && it.rects.isNotEmpty() }
    val isFirstSelPage = selection.firstOrNull()?.page == index
    val isLastSelPage = selection.lastOrNull()?.page == index

    // Local handle-drag bookkeeping. Keys never depend on the selection, so a drag is never restarted.
    var dragSide by remember(doc.handle, index) { mutableIntStateOf(0) }
    var boxX by remember(doc.handle, index) { mutableFloatStateOf(0f) }
    var boxY by remember(doc.handle, index) { mutableFloatStateOf(0f) }
    val latestSelection by rememberUpdatedState(selection)
    val latestStartCb by rememberUpdatedState(onSelectionDragStart)
    val latestMoveCb by rememberUpdatedState(onSelectionDrag)
    val latestEndCb by rememberUpdatedState(onSelectionDragEnd)

    // Safety: if the selection disappears, or the shared drag ended elsewhere, drop the local flag.
    LaunchedEffect(selection.isEmpty()) { if (selection.isEmpty()) dragSide = 0 }

    fun commit() {
        hadFocus = false
        val e = editing ?: return
        editing = null
        if (editText != e.first.values.firstOrNull().orEmpty()) vm.setField(e.first.name, editText)
    }
    DisposableEffect(Unit) {
        onDispose {
            commit()
            // Leaving composition (page scrolled away) must not leave a drag running.
            if (dragSide != 0) { dragSide = 0; latestEndCb() }
        }
    }
    LaunchedEffect(editing) {
        if (editing != null) {
            hadFocus = false
            var focused = false
            repeat(5) {
                if (!focused) {
                    try { focus.requestFocus(); focused = true } catch (_: Exception) { delay(30) }
                }
            }
            keyboard?.show()
        }
    }

    Box(
        Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .background(Color.White)
            .pointerInput(doc.handle, doc.generation, index, fields, links, selection) {
                // Nearest field within a finger-sized margin around each field (small fields are hard to hit exactly).
                fun fieldAt(pos: Offset): Pair<FormField, Widget>? {
                    val sx = size.width / page.width
                    val sy = size.height / page.height
                    val x = pos.x / sx
                    val y = pos.y / sy
                    val mx = 14.dp.toPx() / sx
                    val my = 14.dp.toPx() / sy
                    var best: Pair<FormField, Widget>? = null
                    var bestDist = Float.MAX_VALUE
                    for (f in fields) {
                        if (f.readOnly) continue
                        for (w in f.widgets) {
                            if (w.hidden || w.page != index) continue
                            val dx = maxOf(w.rect.left - x, 0f, x - w.rect.right) / mx
                            val dy = maxOf(w.rect.top - y, 0f, y - w.rect.bottom) / my
                            if (dx > 1f || dy > 1f) continue
                            val d = dx * dx + dy * dy
                            if (d < bestDist) { bestDist = d; best = f to w }
                        }
                    }
                    return best
                }
                fun openField(h: Pair<FormField, Widget>) {
                    val (f, w) = h
                    when {
                        f.readOnly -> onField(f)
                        f.kind == "Text" -> { commit(); editText = f.values.firstOrNull().orEmpty(); editing = f to w }
                        f.kind == "CheckBox" -> { commit(); vm.setField(f.name, !f.values.any { it != "Off" }) }
                        else -> { commit(); onField(f) }
                    }
                }
                fun linkAt(pos: Offset): PdfLink? {
                    val sx = size.width / page.width
                    val sy = size.height / page.height
                    val x = pos.x / sx
                    val y = pos.y / sy
                    val mx = 14.dp.toPx() / sx
                    val my = 14.dp.toPx() / sy
                    var best: PdfLink? = null
                    var bestDist = Float.MAX_VALUE
                    for (l in links) {
                        if (l.page != index) continue
                        val dx = maxOf(l.rect.left - x, 0f, x - l.rect.right) / mx
                        val dy = maxOf(l.rect.top - y, 0f, y - l.rect.bottom) / my
                        if (dx > 1f || dy > 1f) continue
                        val d = dx * dx + dy * dy
                        if (d < bestDist) { bestDist = d; best = l }
                    }
                    return best
                }
                detectTapGestures(
                    onLongPress = { pos ->
                        // Convert screen position to page coordinates (same space as engine).
                        val sx = size.width / page.width
                        val sy = size.height / page.height
                        vm.selectAt(index, pos.x / sx, pos.y / sy)
                    },
                    onPress = { pos ->
                        val link = linkAt(pos)
                        if (link != null) {
                            if (tryAwaitRelease()) vm.onLinkTapped(link)
                        } else {
                            val h = fieldAt(pos)
                            if (h != null && tryAwaitRelease()) openField(h)
                        }
                    },
                    onDoubleTap = { pos ->
                        if (fieldAt(pos) == null && linkAt(pos) == null) onToggleZoom()
                    },
                    onTap = { pos ->
                        if (fieldAt(pos) == null && linkAt(pos) == null) {
                            commit()
                            if (latestSelection.isNotEmpty()) vm.clearSelection()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        shown?.let { (bmp, rot) ->
            // After a rotation the old picture is turned to match until the new one arrives, so the page never flashes white.
            val delta = ((page.rotation - rot) % 360 + 360) % 360
            val swap = delta == 90 || delta == 270
            val w = if (swap) heightPx else widthPx
            val h = if (swap) widthPx else heightPx
            Image(
                bmp.asImageBitmap(), "PDF page ${index + 1}",
                Modifier.requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() }).graphicsLayer { rotationZ = delta.toFloat() },
                contentScale = ContentScale.FillBounds
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / page.width
            val sy = size.height / page.height
            fun paint(r: PdfRect, color: Color) {
                drawRect(color, Offset(r.left * sx, r.top * sy), Size((r.right - r.left) * sx, (r.bottom - r.top) * sy))
            }
            hits.filter { it.page == index }.forEach { h ->
                h.rects.forEach { paint(it, if (h == active) Color(0x88FF8800) else Color(0x66FFE500)) }
            }
            fields.filter { !it.readOnly }.flatMap { it.widgets }.filter { it.page == index && !it.hidden }.forEach { paint(it.rect, Color(0x18008877)) }
            sel?.rects?.forEach { paint(it, Color(0x553A86FF)) }
        }
        editing?.let { (f, w) ->
            val sx = widthPx / page.width
            val sy = heightPx / page.height
            val left = (w.rect.left * sx).roundToInt()
            val top = (w.rect.top * sy).roundToInt()
            val boxW = ((w.rect.right - w.rect.left) * sx).coerceAtLeast(60f)
            val boxH = ((w.rect.bottom - w.rect.top) * sy).coerceAtLeast(28f)
            val multiline = (w.rect.bottom - w.rect.top) > 40f
            val fontPx = if (multiline) 11f * sx else (boxH * 0.6f)
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(left, top) }
                    .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                    .background(Color(0xFFDCE8FF))
                    .padding(horizontal = 3.dp),
                contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart
            ) {
                BasicTextField(
                    value = editText,
                    onValueChange = { v -> editText = if (f.maxLength != null) v.take(f.maxLength) else v },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        .onFocusChanged { s -> if (s.isFocused) hadFocus = true else if (hadFocus) commit() },
                    textStyle = TextStyle(color = Color.Black, fontSize = with(density) { fontPx.toSp() }),
                    singleLine = !multiline,
                    cursorBrush = SolidColor(Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() })
                )
            }
        }
        // Floating Copy bar: shown on the first selected page only, and hidden while dragging.
        if (sel != null && isFirstSelPage && dragSide == 0 && !isDraggingSelection()) {
            val sx = widthPx / page.width
            val sy = heightPx / page.height
            val first = sel.rects.first()
            val left = (first.left * sx).roundToInt()
            val top = (first.top * sy).roundToInt() - with(density) { 48.dp.roundToPx() }
            Surface(
                color = Color(0xE6303134),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(left.coerceAtLeast(0), top.coerceAtLeast(0)) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { vm.copySelection() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) { Text("Copy") }
                    TextButton(
                        onClick = { vm.selectAll(index) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) { Text("Select all") }
                }
            }
        }
        // Selection handles. IMPORTANT: a handle's touch box stays composed while its own drag is
        // running, even if this page stops being the first/last selected page. Removing the node
        // mid-gesture would cancel the drag WITHOUT calling onDragEnd/onDragCancel.
        val showStartHandle = sel != null && (isFirstSelPage || dragSide == 1)
        val showEndHandle = sel != null && (isLastSelPage || dragSide == 2)
        if (sel != null && (showStartHandle || showEndHandle)) {
            val sx = widthPx / page.width
            val sy = heightPx / page.height
            val first = sel.rects.first()
            val last = sel.rects.last()
            val r = with(density) { 11.dp.toPx() }
            val gapY = with(density) { 2.dp.toPx() }
            val touch = with(density) { 32.dp.toPx() }
            val handleColor = Color(0xFF3A86FF)

            // Both handles hang below the text line; the squared corner faces the text.
            val startCx = (first.left * sx - r).coerceAtLeast(r)
            val startCy = first.bottom * sy + gapY + r
            val endCx = (last.right * sx + r).coerceAtMost(widthPx - r)
            val endCy = last.bottom * sy + gapY + r

            Canvas(Modifier.fillMaxSize()) {
                if (isFirstSelPage) {
                    drawCircle(handleColor, radius = r, center = Offset(startCx, startCy))
                    drawRect(handleColor, topLeft = Offset(startCx, startCy - r), size = Size(r, r))
                }
                if (isLastSelPage) {
                    drawCircle(handleColor, radius = r, center = Offset(endCx, endCy))
                    drawRect(handleColor, topLeft = Offset(endCx - r, endCy - r), size = Size(r, r))
                }
            }

            val latestSy by rememberUpdatedState(sy)
            val startOrigin by rememberUpdatedState(Offset(startCx - touch / 2, startCy - touch / 2))
            val endOrigin by rememberUpdatedState(Offset(endCx - touch / 2, endCy - touch / 2))

            // START handle: the anchor is the LAST glyph of the whole selection (may be on another page).
            if (showStartHandle) {
                val origin = if (dragSide == 1) Offset(boxX, boxY) else startOrigin
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
                        .size(with(density) { touch.toDp() })
                        .pointerInput(doc.handle, doc.generation, index, "startHandle") {
                            detectDragGestures(
                                onDragStart = { off ->
                                    val o = startOrigin
                                    val whole = latestSelection
                                    val anchorPage = whole.lastOrNull()?.page ?: index
                                    val anchorGlyph = ((whole.lastOrNull()?.end ?: 1) - 1).coerceAtLeast(0)
                                    boxX = o.x; boxY = o.y
                                    dragSide = 1
                                    latestStartCb(1, anchorPage, anchorGlyph, o.x + off.x, o.y + off.y, index)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    latestMoveCb(boxX + change.position.x, boxY + change.position.y, index)
                                },
                                onDragEnd = { dragSide = 0; latestEndCb() },
                                onDragCancel = { dragSide = 0; latestEndCb() }
                            )
                        }
                )
            }
            // END handle: the anchor is the FIRST glyph of the whole selection.
            if (showEndHandle) {
                val origin = if (dragSide == 2) Offset(boxX, boxY) else endOrigin
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
                        .size(with(density) { touch.toDp() })
                        .pointerInput(doc.handle, doc.generation, index, "endHandle") {
                            detectDragGestures(
                                onDragStart = { off ->
                                    val o = endOrigin
                                    val whole = latestSelection
                                    val anchorPage = whole.firstOrNull()?.page ?: index
                                    val anchorGlyph = whole.firstOrNull()?.start ?: 0
                                    boxX = o.x; boxY = o.y
                                    dragSide = 2
                                    latestStartCb(2, anchorPage, anchorGlyph, o.x + off.x, o.y + off.y, index)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    latestMoveCb(boxX + change.position.x, boxY + change.position.y, index)
                                },
                                onDragEnd = { dragSide = 0; latestEndCb() },
                                onDragCancel = { dragSide = 0; latestEndCb() }
                            )
                        }
                )
            }
        }
        if (shown == null || error != null) Text(error ?: "Rendering...", color = if (error != null) Color.Red else Color.DarkGray)
    }
}
