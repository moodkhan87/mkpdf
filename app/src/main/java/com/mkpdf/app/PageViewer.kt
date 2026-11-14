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

/**
 * Continuous vertical list of pages. Pinch zooms around the fingers (50% to 400%), one finger scrolls up/down
 * and, when zoomed in, also pans sideways. Page images are re-rendered only after the zoom has settled.
 * A scroll handle with a page bubble appears while touching and fades out afterwards.
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

    // Re-render the page images only after the pinch has settled.
    LaunchedEffect(zoom) { delay(250); renderZoom = zoom }
    // Show the scroll handle on touch or scroll; hide it two seconds after the last activity.
    LaunchedEffect(touchTick, scrolling, dragging) {
        showHandle = true
        if (!scrolling && !dragging) { delay(2000); showHandle = false }
    }

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
                        PdfPageItem(doc, index, pageW, renderW, ui.hits, active, ui.fields, ui.links, ui.selection, vm, onField) {
                            zoomBy(if (zoom > 1.1f) 1f else 2f, vw / 2f, vh / 2f)
                        }
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
                            onDragStart = { dragFraction = progress; dragging = true },
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
    links: List<PdfLink>, selection: TextSelection?, vm: EditorViewModel, onField: (FormField) -> Unit, onToggleZoom: () -> Unit
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
    fun commit() {
        hadFocus = false
        val e = editing ?: return
        editing = null
        if (editText != e.first.values.firstOrNull().orEmpty()) vm.setField(e.first.name, editText)
    }
    DisposableEffect(Unit) { onDispose { commit() } }
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
            .pointerInput(doc.handle, doc.generation, index, fields, links) {
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
                    // Act as soon as the finger lifts instead of waiting to see whether it is a double tap.
                    onPress = { pos ->
                        val link = linkAt(pos)
                        if (link != null) {
                            if (tryAwaitRelease()) vm.onLinkTapped(link)
                        } else {
                            val h = fieldAt(pos)
                            if (h != null && tryAwaitRelease()) openField(h)
                        }
                    },
                    onDoubleTap = { pos -> if (fieldAt(pos) == null && linkAt(pos) == null) onToggleZoom() },
                    onTap = { pos -> if (fieldAt(pos) == null && linkAt(pos) == null) commit() }
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
        if (shown == null || error != null) Text(error ?: "Rendering…", color = if (error != null) Color.Red else Color.DarkGray)
    }
}
