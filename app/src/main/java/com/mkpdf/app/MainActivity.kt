package com.mkpdf.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF005B50))) {
                EditorApp(vm)
            }
        }
    }
}

@Composable
fun EditorApp(vm: EditorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val doc = ui.doc
    val listState = rememberLazyListState()
    val pageCount = doc?.pages?.size ?: 0
    // The current page is the one whose centre is closest to the middle of the screen.
    val page by remember(pageCount) {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val best = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
            best.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }
    var menu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var importPages by remember { mutableStateOf<List<Int>?>(null) }
    var importAt by remember { mutableIntStateOf(0) }
    var imageRect by remember { mutableStateOf<List<Double>?>(null) }
    var imageAsPage by remember { mutableStateOf(false) }
    var imagePage by remember { mutableIntStateOf(0) }
    var selectedField by remember { mutableStateOf<String?>(null) }
    var combineBefore by remember { mutableStateOf(false) }
    var splitRanges by remember { mutableStateOf<List<IntRange>>(emptyList()) }
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var closeAsk by remember { mutableStateOf(false) }
    var closeAfterSave by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    fun replaceSafely(action: () -> Unit) { if (doc?.dirty == true) discardAction = action else action() }
    fun closeSearch() { searchOpen = false; vm.search("") }
    fun requestClose() { if (doc?.dirty == true) closeAsk = true else vm.closeDocument() }

    val open = rememberLauncherForActivityResult(OpenPdfWithWrite()) { it?.let(vm::open) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { if (it == null) closeAfterSave = false else vm.saveAs(it) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.importPdf(uri, importAt, importPages) } }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.importImage(uri, imagePage, imageRect, imageAsPage) } }
    val combine = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.combineWithCurrent(it, combineBefore) }
    val split = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { tree -> vm.splitRanges(tree, splitRanges) } }

    LaunchedEffect(doc?.handle) { if (doc != null) listState.scrollToItem(0) }
    LaunchedEffect(ui.selectedHit, ui.hits) { ui.hits.getOrNull(ui.selectedHit)?.let { listState.animateScrollToItem(it.page) } }
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }
    BackHandler(enabled = doc != null || searchOpen) { if (searchOpen) closeSearch() else requestClose() }
    LaunchedEffect(closeAfterSave, doc?.dirty, ui.busy, ui.error) {
        if (closeAfterSave && ui.error != null) closeAfterSave = false
        else if (closeAfterSave && doc != null && !doc.dirty && !ui.busy) { closeAfterSave = false; vm.closeDocument() }
    }

    val ready = doc != null && !ui.busy

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searchOpen) {
                        IconButton(onClick = { closeSearch() }) { Icon(MkIcons.Close, "Close search") }
                        OutlinedTextField(ui.query, { vm.search(it) }, placeholder = { Text("Search text") }, singleLine = true,
                            modifier = Modifier.weight(1f).focusRequester(searchFocus))
                        IconButton(onClick = { vm.navigateHit(-1) }, enabled = ui.hits.isNotEmpty()) { Icon(MkIcons.ArrowUp, "Previous match") }
                        IconButton(onClick = { vm.navigateHit(1) }, enabled = ui.hits.isNotEmpty()) { Icon(MkIcons.ArrowDown, "Next match") }
                    } else {
                        if (doc != null) IconButton(onClick = { requestClose() }, enabled = !ui.busy) { Icon(MkIcons.ArrowBack, "Close file") }
                        Text("${doc?.name ?: "MKpdf"}${if (doc?.dirty == true) " *" else ""}", Modifier.weight(1f).padding(horizontal = 12.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { searchOpen = true }, enabled = ready) { Icon(MkIcons.Search, "Search") }
                        IconButton(onClick = { vm.edit("undo") }, enabled = doc?.undo == true && !ui.busy) { Icon(MkIcons.Undo, "Undo") }
                        IconButton(onClick = { vm.edit("redo") }, enabled = doc?.redo == true && !ui.busy) { Icon(MkIcons.Redo, "Redo") }
                        IconButton(onClick = { vm.saveInPlace { save.launch(doc?.name ?: "MKpdf.pdf") } }, enabled = doc?.dirty == true && !ui.busy) { Icon(MkIcons.Save, "Save") }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = !ui.busy) { Icon(MkIcons.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                @Composable fun item(label: String, enabled: Boolean = true, action: () -> Unit) =
                                    DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { menu = false; action() })
                                item("Open") { replaceSafely { open.launch(arrayOf("application/pdf")) } }
                                item("New") { replaceSafely { vm.newDocument() } }
                                item("Save As", doc != null) { save.launch(doc?.name ?: "MKpdf.pdf") }
                                item("Close", doc != null) { requestClose() }
                                item("Rotate Right", doc != null) { vm.rotateAll(90) }
                                item("Rotate Left", doc != null) { vm.rotateAll(270) }
                                item("Edit", doc != null) { vm.loadLines(page); dialog = "lines" }
                                item("Add Text", doc != null) { dialog = "text" }
                                item("Add Image", doc != null) { dialog = "image" }
                                item("Add Blank Page", doc != null) { dialog = "blank" }
                                item("Insert Page from PDF", doc != null) { dialog = "import" }
                                item("Delete Page", doc != null) { dialog = "delete" }
                                item("Combine files", doc != null) { dialog = "combine" }
                                item("Split files", doc != null) { dialog = "split" }
                            }
                        }
                    }
                }
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (searchOpen && ui.query.isNotBlank())
            Text("${if (ui.selectedHit >= 0) ui.selectedHit + 1 else 0} / ${ui.hits.size} matches${if (ui.searching) " · searching…" else ""}", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (doc == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Open a PDF or create a blank document.", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { open.launch(arrayOf("application/pdf")) }, enabled = !ui.busy) { Text("Open") }
                    OutlinedButton(onClick = { vm.newDocument() }, enabled = !ui.busy) { Text("New") }
                }
            }
        } else {
            Box(Modifier.weight(1f)) {
                PdfPages(doc, ui, vm, listState) { f -> selectedField = f.name; dialog = "fields" }
            }
            if (doc.notices.isNotEmpty()) Text(doc.notices.joinToString("\n"), Modifier.heightIn(max = 80.dp).padding(8.dp), color = MaterialTheme.colorScheme.error)
        }
    }

    if (discardAction != null) AlertDialog(onDismissRequest = { discardAction = null }, title = { Text("Replace the working document?") },
        text = { Text("Save a copy first if you need these edits. Continuing replaces the single local recovery slot.") },
        confirmButton = { TextButton(onClick = { val action = discardAction; discardAction = null; action?.invoke() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { discardAction = null }) { Text("Cancel") } })
    if (closeAsk) AlertDialog(onDismissRequest = { closeAsk = false }, title = { Text("Close file?") },
        text = {
            Column {
                Text("This file has unsaved changes.")
                TextButton(onClick = { closeAsk = false; closeAfterSave = true; save.launch(doc?.name ?: "MKpdf.pdf") }) { Text("Save As") }
                TextButton(onClick = { closeAsk = false; vm.closeDocument() }) { Text("Ignore changes and close") }
                TextButton(onClick = { closeAsk = false }) { Text("Cancel") }
            }
        }, confirmButton = {})
    ui.error?.let { AlertDialog(onDismissRequest = vm::dismissError, title = { Text("MKpdf") }, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } }) }
    ui.message?.let { AlertDialog(onDismissRequest = vm::dismissMessage, title = { Text("MKpdf") }, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } }) }

    when (dialog) {
        "blank" -> PageInsertDialog("Add Blank Page", pageCount, page + 1, "Add", { dialog = "" }) { after, no ->
            dialog = ""; vm.edit("insert_blank", "at" to if (after) no else no - 1)
        }
        "import" -> PageInsertDialog("Insert Page from PDF", pageCount, page + 1, "Choose PDF", { dialog = "" }) { after, no ->
            importPages = null; importAt = if (after) no else no - 1; dialog = ""; import.launch(arrayOf("application/pdf"))
        }
        "delete" -> DeletePagesDialog(pageCount, page + 1, { dialog = "" }) { pages -> dialog = ""; vm.deletePages(pages) }
        "combine" -> CombinePositionDialog({ dialog = "" }) { before -> combineBefore = before; dialog = ""; combine.launch(arrayOf("application/pdf")) }
        "split" -> SplitDialog(pageCount, { dialog = "" }) { ranges -> splitRanges = ranges; dialog = ""; split.launch(null) }
        "text", "image" -> PlacementDialog(dialog == "image", { dialog = "" }) { text, rect, size ->
            if (dialog == "text") vm.edit("add_text", "page" to page, "text" to text, "rect" to rect, "size" to size)
            else { imagePage = page; imageRect = rect; imageAsPage = false; images.launch(arrayOf("image/png", "image/jpeg")) }
            dialog = ""
        }
        "lines" -> ExistingTextDialog(ui.lines, ui.busy, { dialog = "" }) { line, text -> vm.edit("edit_line", "page" to page, "line" to line.index, "text" to text); dialog = "" }
        "fields" -> Panel("Fill AcroForm fields", { dialog = "" }) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.fields.isEmpty() && !ui.busy) Text("No AcroForm fields found. This is not OCR.")
            val fields = ui.fields.sortedBy { if (it.name == selectedField) 0 else 1 }
            fields.forEach { f -> key(f.name) { FieldEditor(f, ui.busy) { vm.setField(f.name, it) } } }
        }
    }
}
