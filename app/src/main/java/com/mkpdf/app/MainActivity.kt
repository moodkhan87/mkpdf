package com.mkpdf.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private val vm:EditorViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF005B50))) {EditorApp(vm)}}
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun EditorApp(vm:EditorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val doc=ui.doc
    val scope=rememberCoroutineScope()
    val pager=rememberPagerState(pageCount={doc?.pages?.size?:0})
    val page=pager.currentPage.coerceAtMost((doc?.pages?.size?:1)-1).coerceAtLeast(0)
    var menu by remember {mutableStateOf(false)}
    var dialog by remember {mutableStateOf("")}
    var range by remember {mutableStateOf("all")}
    var rangeError by remember {mutableStateOf<String?>(null)}
    var importPages by remember {mutableStateOf<List<Int>?>(null)}
    var importAt by remember {mutableIntStateOf(0)}
    var imageRect by remember {mutableStateOf<List<Double>?>(null)}
    var imageAsPage by remember {mutableStateOf(false)}
    var imagePage by remember {mutableIntStateOf(0)}
    var selectedField by remember {mutableStateOf<String?>(null)}
    var combineUris by remember {mutableStateOf<List<Uri>>(emptyList())}
    var discardAction by remember {mutableStateOf<(() -> Unit)?>(null)}
    fun replaceSafely(action:()->Unit) {if(doc?.dirty==true)discardAction=action else action()}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(vm::open)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {it?.let(vm::saveAs)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->vm.importPdf(uri,importAt,importPages)}}
    val images=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->vm.importImage(uri,imagePage,imageRect,imageAsPage)}}
    val combine=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {if(it.size>=2){combineUris=it;dialog="combine"}}
    val split=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {it?.let(vm::splitAll)}
    LaunchedEffect(doc?.handle) {if(doc!=null)pager.scrollToPage(0)}
    LaunchedEffect(ui.selectedHit,ui.hits) {ui.hits.getOrNull(ui.selectedHit)?.let {pager.animateScrollToPage(it.page)}}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={replaceSafely {open.launch(arrayOf("application/pdf"))}},enabled=!ui.busy){Text("Open")}
            TextButton(onClick={replaceSafely {vm.newDocument()}},enabled=!ui.busy){Text("New")}
            TextButton(onClick={save.launch(doc?.name?:"MKpdf.pdf")},enabled=doc!=null&&!ui.busy){Text("Save as")}
            TextButton(onClick={vm.edit("undo")},enabled=doc?.undo==true&&!ui.busy){Text("Undo")}
            TextButton(onClick={vm.edit("redo")},enabled=doc?.redo==true&&!ui.busy){Text("Redo")}
            Box {
                TextButton(onClick={menu=true},enabled=!ui.busy){Text("Tools")}
                DropdownMenu(menu,{menu=false}) {
                    fun close(action:()->Unit) {menu=false;action()}
                    DropdownMenuItem(text={Text("Reopen recent PDF")},onClick={close {replaceSafely {vm.reopenRecent()}}})
                    DropdownMenuItem(text={Text("Combine PDF files…")},onClick={close {replaceSafely {combine.launch(arrayOf("application/pdf"))}}})
                    if(doc!=null) {
                        DropdownMenuItem(text={Text("Move page earlier")},enabled=page>0,onClick={close {vm.edit("move","page" to page,"to" to page-1)}})
                        DropdownMenuItem(text={Text("Move page later")},enabled=page<doc.pages.lastIndex,onClick={close {vm.edit("move","page" to page,"to" to page+1)}})
                        DropdownMenuItem(text={Text("Delete current page…")},onClick={close {dialog="delete"}})
                        DropdownMenuItem(text={Text("Insert blank after page")},onClick={close {vm.edit("insert_blank","at" to page+1)}})
                        DropdownMenuItem(text={Text("Insert pages from PDF…")},onClick={close {range="all";rangeError=null;dialog="import"}})
                        for(degrees in listOf(90,180,270))DropdownMenuItem(text={Text("Rotate page $degrees°")},onClick={close {vm.edit("rotate","page" to page,"degrees" to degrees)}})
                        DropdownMenuItem(text={Text("Add text box…")},onClick={close {dialog="text"}})
                        DropdownMenuItem(text={Text("Edit existing text…")},onClick={close {vm.loadLines(page);dialog="lines"}})
                        DropdownMenuItem(text={Text("Place image on page…")},onClick={close {dialog="image"}})
                        DropdownMenuItem(text={Text("Insert image as new page…")},onClick={close {imagePage=page;imageAsPage=true;imageRect=null;images.launch(arrayOf("image/png","image/jpeg"))}})
                        DropdownMenuItem(text={Text("Fill form fields…")},onClick={close {selectedField=null;vm.loadFields();dialog="fields"}})
                        DropdownMenuItem(text={Text("Split into individual PDFs…")},onClick={close {dialog="split"}})
                    }
                }
            }
        }
        Text("MKpdf · ${doc?.name?:"local PDF editor"}${if(doc?.dirty==true)" *" else ""}",Modifier.padding(horizontal=12.dp),maxLines=1)
        if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.recoveryAvailable)Row {
            TextButton(onClick={replaceSafely {vm.recover()}},enabled=!ui.busy){Text("Recover last local working PDF")}
        }
        if(doc==null) {
            Column(Modifier.weight(1f).padding(24.dp)) {
                Text("Open a PDF or create a blank document.",style=MaterialTheme.typography.titleLarge)
                Text("Files stay on this device. Use Save as to write your edited PDF through Android's file picker.")
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(ui.query,{vm.search(it)},label={Text("Search text")},singleLine=true,modifier=Modifier.weight(1f),enabled=!ui.busy)
                TextButton(onClick={vm.navigateHit(-1)},enabled=ui.hits.isNotEmpty()){Text("‹")}
                TextButton(onClick={vm.navigateHit(1)},enabled=ui.hits.isNotEmpty()){Text("›")}
            }
            if(ui.query.isNotBlank())Text("${if(ui.selectedHit>=0)ui.selectedHit+1 else 0} / ${ui.hits.size} matches${if(ui.searching)" · searching…" else ""}",Modifier.padding(horizontal=12.dp))
            Box(Modifier.weight(1f)) {
                HorizontalPager(pager,beyondBoundsPageCount=0,key={"${doc.handle}:$it"},userScrollEnabled=!ui.busy) {index ->
                    PageViewer(doc,index,ui.hits,ui.hits.getOrNull(ui.selectedHit),ui.fields,vm) {f ->selectedField=f.name;dialog="fields"}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={scope.launch {pager.animateScrollToPage((page-1).coerceAtLeast(0))}},enabled=page>0&&!ui.busy){Text("Previous")}
                Text("${page+1} / ${doc.pages.size}",Modifier.padding(top=12.dp))
                TextButton(onClick={scope.launch {pager.animateScrollToPage((page+1).coerceAtMost(doc.pages.lastIndex))}},enabled=page<doc.pages.lastIndex&&!ui.busy){Text("Next")}
            }
            Text("Pinch to zoom · double-tap to reset/zoom",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
            if(doc.notices.isNotEmpty())Text(doc.notices.joinToString("\n"),Modifier.heightIn(max=80.dp).padding(8.dp),color=MaterialTheme.colorScheme.error)
        }
    }
    if(discardAction!=null)AlertDialog(onDismissRequest={discardAction=null},title={Text("Replace the working document?")},
        text={Text("Save a copy first if you need these edits. Continuing replaces the single local recovery slot.")},
        confirmButton={TextButton(onClick={val action=discardAction;discardAction=null;action?.invoke()}){Text("Continue")}},
        dismissButton={TextButton(onClick={discardAction=null}){Text("Cancel")}})
    ui.error?.let {AlertDialog(onDismissRequest=vm::dismissError,title={Text("MKpdf")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissError){Text("OK")}})}
    ui.message?.let {AlertDialog(onDismissRequest=vm::dismissMessage,title={Text("MKpdf")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissMessage){Text("OK")}})}
    when(dialog) {
        "delete" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Delete page ${page+1}?")},text={Text("Undo is available. The final page cannot be deleted.")},
            confirmButton={TextButton(onClick={dialog="";vm.edit("delete","page" to page)}){Text("Delete")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "text","image" -> PlacementDialog(dialog=="image",{dialog=""}) {text,rect,size ->
            if(dialog=="text")vm.edit("add_text","page" to page,"text" to text,"rect" to rect,"size" to size)
            else {imagePage=page;imageRect=rect;imageAsPage=false;images.launch(arrayOf("image/png","image/jpeg"))}
            dialog=""
        }
        "lines" -> ExistingTextDialog(ui.lines,ui.busy,{dialog=""}) {line,text->vm.edit("edit_line","page" to page,"line" to line.index,"text" to text);dialog=""}
        "fields" -> Panel("Fill AcroForm fields",{dialog=""}) {
            if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.fields.isEmpty()&&!ui.busy)Text("No AcroForm fields found. This is not OCR.")
            val fields=ui.fields.sortedBy {if(it.name==selectedField)0 else 1}
            fields.forEach {f->key(f.name) {FieldEditor(f,ui.busy) {vm.setField(f.name,it)}}}
        }
        "import" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Import pages after page ${page+1}")},
            text={Column {Text("Source pages: all, or 1,3-5. Imported form name collisions are renamed.")
                OutlinedTextField(range,{range=it},label={Text("Pages")});rangeError?.let {Text(it)}}},
            confirmButton={TextButton(onClick={try {importPages=parsePageRange(range);importAt=page+1;dialog="";import.launch(arrayOf("application/pdf"))}catch(e:Exception){rangeError=e.message}}){Text("Choose PDF")}},
            dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "split" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Split into individual PDF pages?")},
            text={Text("Choose an output folder. Each page becomes its own PDF. If a provider fails, already-written files remain; the original is unchanged.")},
            confirmButton={TextButton(onClick={dialog="";split.launch(null)}){Text("Choose folder")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}})
        "combine" -> Panel("Combine in this order",{dialog=""}) {
            combineUris.forEachIndexed {i,uri ->
                Text("${i+1}. ${uri.lastPathSegment?:uri}")
                Row {
                    TextButton(onClick={val a=combineUris.toMutableList();val x=a.removeAt(i);a.add(i-1,x);combineUris=a},enabled=i>0){Text("Earlier")}
                    TextButton(onClick={val a=combineUris.toMutableList();val x=a.removeAt(i);a.add(i+1,x);combineUris=a},enabled=i<combineUris.lastIndex){Text("Later")}
                }
            }
            Button(onClick={vm.combine(combineUris);dialog=""},enabled=!ui.busy){Text("Combine PDFs")}
        }
    }
}
