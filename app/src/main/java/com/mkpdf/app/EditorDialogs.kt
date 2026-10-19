package com.mkpdf.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun Panel(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth(.96f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(title,style=MaterialTheme.typography.titleLarge);TextButton(onClick=onDismiss){Text("Close")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),content=content)
            }
        }
    }
}
@Composable fun PlacementDialog(image:Boolean,onDismiss:()->Unit,onApply:(String,List<Double>,Double)->Unit) {
    var text by remember {mutableStateOf("")}
    var x by remember {mutableStateOf("36")};var y by remember {mutableStateOf("36")}
    var w by remember {mutableStateOf(if(image)"240" else "300")};var h by remember {mutableStateOf(if(image)"180" else "72")}
    var size by remember {mutableStateOf("18")};var error by remember {mutableStateOf<String?>(null)}
    Panel(if(image)"Place an image" else "Add text box",onDismiss) {
        Text("Coordinates are displayed-page points (1/72 inch), from the top-left. Width and height control placement and scale.")
        if(!image)OutlinedTextField(text,{text=it},label={Text("Text")},modifier=Modifier.fillMaxWidth())
        Row {OutlinedTextField(x,{x=it},label={Text("Left")},modifier=Modifier.weight(1f));OutlinedTextField(y,{y=it},label={Text("Top")},modifier=Modifier.weight(1f))}
        Row {OutlinedTextField(w,{w=it},label={Text("Width")},modifier=Modifier.weight(1f));OutlinedTextField(h,{h=it},label={Text("Height")},modifier=Modifier.weight(1f))}
        if(!image)OutlinedTextField(size,{size=it},label={Text("Font size (pt)")})
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={
            val a=x.toDoubleOrNull();val b=y.toDoubleOrNull();val c=w.toDoubleOrNull();val d=h.toDoubleOrNull();val fs=size.toDoubleOrNull()
            if(a==null||b==null||c==null||d==null||fs==null||!listOf(a,b,c,d,fs).all {it.isFinite()}||c<=0||d<=0||fs !in 1.0..512.0||(!image&&text.isBlank()))error="Enter valid coordinates, positive dimensions, and text."
            else onApply(text,listOf(a,b,a+c,b+d),fs)
        }) {Text(if(image)"Choose image" else "Add to page")}
    }
}
@Composable fun ExistingTextDialog(lines:List<TextLine>,busy:Boolean,onDismiss:()->Unit,onSave:(TextLine,String)->Unit) {
    var selected by remember {mutableStateOf<TextLine?>(null)}
    var text by remember {mutableStateOf("")}
    Panel("Edit existing PDF text",onDismiss) {
        Text("This changes content-stream text, not a cover rectangle. Unsupported encodings or fonts may prevent faithful replacement.")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(lines.isEmpty()&&!busy)Text("No editable text lines were extracted on this page.")
        if(selected==null)lines.forEach {l ->
            ListItem(headlineContent={Text(l.text)},supportingContent={if(!l.editable)Text("Undecodable — unavailable")},
                modifier=Modifier.clickable(enabled=l.editable){selected=l;text=l.text})
        }else {
            OutlinedTextField(text,{text=it},label={Text("Replacement")},modifier=Modifier.fillMaxWidth())
            Button(onClick={selected?.let {onSave(it,text)}},enabled=!busy){Text("Replace original text")}
            TextButton(onClick={selected=null}){Text("Back to lines")}
        }
    }
}
@Composable fun FieldEditor(f:FormField,busy:Boolean,onSave:(Any?)->Unit) {
    var text by remember(f.name,f.values) {mutableStateOf(f.values.firstOrNull().orEmpty())}
    var chosen by remember(f.name,f.values) {mutableStateOf(f.values.toSet())}
    var checked by remember(f.name,f.values) {mutableStateOf(f.values.any {it!="Off"})}
    val enabled=!f.readOnly&&!busy
    Text(f.name,style=MaterialTheme.typography.titleMedium)
    if(f.readOnly)Text("Read-only field")
    when(f.kind) {
        "Text" -> {
            OutlinedTextField(text,{v -> text=if(f.maxLength!=null)v.take(f.maxLength) else v},enabled=enabled,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={onSave(text)},enabled=enabled){Text("Apply text")}
        }
        "CheckBox" -> {
            Row {Checkbox(checked,{checked=it},enabled=enabled);Text(if(checked)"Checked" else "Unchecked")}
            TextButton(onClick={onSave(checked)},enabled=enabled){Text("Apply checkbox")}
        }
        "Radio","List","Combo" -> {
            f.options.forEach {o ->
                Row(Modifier.fillMaxWidth().clickable(enabled=enabled) {
                    chosen=if(f.multi) {if(o.value in chosen)chosen-o.value else chosen+o.value} else setOf(o.value)
                }) {
                    if(f.multi)Checkbox(o.value in chosen,null,enabled=enabled)
                    else RadioButton(o.value in chosen,null,enabled=enabled)
                    Text(o.label,Modifier.padding(top=12.dp))
                }
            }
            if(f.editableChoice)OutlinedTextField(text,{text=it},enabled=enabled,label={Text("Or type a custom value")})
            TextButton(onClick={
                onSave(if(f.kind=="Radio")chosen.firstOrNull() else if(f.editableChoice&&text!=f.values.firstOrNull().orEmpty())listOf(text) else chosen.toList())
            },enabled=enabled){Text("Apply selection")}
        }
        else -> Text("${f.kind} fields are not editable in MKpdf.")
    }
    HorizontalDivider(Modifier.padding(vertical=8.dp))
}
fun parsePageRange(text:String):List<Int>? {
    if(text.isBlank()||text.trim().equals("all",true))return null
    val pages=mutableListOf<Int>()
    for(part in text.split(',')) {
        val ends=part.trim().split('-')
        require(ends.size in 1..2){"Use pages such as 1,3-5"}
        val first=ends[0].trim().toInt();val last=if(ends.size==2)ends[1].trim().toInt()else first
        require(first>=1&&last>=first&&last-first<10000){"Invalid page range"}
        pages+=(first..last).map {it-1}
        require(pages.size<=10000){"Too many selected pages"}
    }
    return pages.distinct()
}
