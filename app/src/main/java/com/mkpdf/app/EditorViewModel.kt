package com.mkpdf.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class EditorViewModel(app:Application):AndroidViewModel(app) {
    private val repo=PdfRepository(app)
    private val _ui=MutableStateFlow(UiState())
    val ui=_ui.asStateFlow()
    private var searchJob:Job?=null
    init { perform {repo.run {init()};_ui.update {it.copy(recoveryAvailable=repo.saf.recovery.isFile)}} }
    fun dismissError() {_ui.update {it.copy(error=null)}}
    fun dismissMessage() {_ui.update {it.copy(message=null)}}
    private fun perform(block:suspend ()->Unit) {
        if(_ui.value.busy)return
        viewModelScope.launch {
            _ui.update {it.copy(busy=true,error=null)}
            try {block()} catch(e:CancellationException){throw e}
            catch(e:Exception){_ui.update {it.copy(error=e.message?:"Operation failed")}}
            finally {_ui.update {it.copy(busy=false)}}
        }
    }
    private suspend fun publish(value:JSONObject) {
        val d=value.document()
        _ui.update {it.copy(doc=d,lines=emptyList(),fields=emptyList())}
        try {repo.run {call("autosave",d)}}
        catch(e:Exception) {_ui.update {it.copy(error="Edit succeeded, but local recovery could not be saved: ${e.message}")}}
        search(_ui.value.query)
    }
    private suspend fun replace(value:JSONObject) {
        val old=_ui.value.doc
        searchJob?.cancel()
        publish(value)
        if(old!=null && old.handle!=value.getLong("handle"))repo.run {call("close",old);clearCache()}
    }
    fun newDocument()=perform {replace(repo.run {call("blank")})}
    fun open(uri:Uri)=perform {
        val value=repo.run {
            val (name,file)=saf.stage(uri)
            try {val d=call("open",null,"path" to file.absolutePath,"name" to name);saf.persistRead(uri);d}
            finally {file.delete()}
        }; replace(value)
    }
    fun reopenRecent() {repo.saf.recent()?.let(::open)}
    fun recover()=perform {
        val d=repo.run {call("open",null,"path" to saf.recovery.absolutePath,"name" to "Recovered.pdf")}
        replace(d);_ui.update {it.copy(recoveryAvailable=false)}
    }
    fun edit(op:String,vararg args:Pair<String,Any?>) {
        val d=_ui.value.doc?:return
        searchJob?.cancel()
        perform {publish(repo.run {call(op,d,*args)})}
    }
    fun importPdf(uri:Uri,at:Int,pages:List<Int>?=null) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val (name,file)=saf.stage(uri)
                try {call("insert_pdf",d,"path" to file.absolutePath,"name" to name,"at" to at,"pages" to pages)}
                finally {file.delete()}
            };publish(value)
        }
    }
    fun importImage(uri:Uri,page:Int,rect:List<Double>?,asPage:Boolean) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val (name,file)=saf.stage(uri)
                try {call(if(asPage)"image_page" else "add_image",d,"path" to file.absolutePath,"name" to name,
                    "page" to page,"at" to (page+1),"rect" to rect)} finally {file.delete()}
            };publish(value)
        }
    }
    fun combine(uris:List<Uri>)=perform {
        if(uris.size<2)throw IllegalArgumentException("Select at least two PDFs")
        val value=repo.run {
            val staged=mutableListOf<Pair<String,File>>()
            try {
                uris.forEach {staged+=saf.stage(it)}
                call("combine",null,"inputs" to staged.map {(name,file)->obj("name" to name,"path" to file.absolutePath)})
            } finally {staged.forEach {it.second.delete()}}
        };replace(value)
    }
    fun saveAs(uri:Uri) {
        val d=_ui.value.doc?:return
        perform {
            val value=repo.run {
                val snap=call("snapshot",d)
                saf.write(uri,File(snap.getString("path")))
                call("mark_saved",d)
            };publish(value);_ui.update {it.copy(message="Saved a PDF copy. Original source was not overwritten.")}
        }
    }
    fun splitAll(tree:Uri) {
        val d=_ui.value.doc?:return
        perform {
            var count=0
            try {
                repo.run {
                    for(page in d.pages.indices) {
                        coroutineContext.ensureActive()
                        val out=call("extract",d,"pages" to listOf(page))
                        val dest=saf.child(tree,"${d.name.substringBeforeLast('.')}-${page+1}.pdf")
                        saf.write(dest,File(out.getString("path")));count++
                    }
                }
                _ui.update {it.copy(message="Wrote $count separate PDF pages.")}
            }catch(e:Exception){throw IllegalStateException("Split stopped after $count files: ${e.message}",e)}
        }
    }
    fun loadLines(page:Int) {
        val d=_ui.value.doc?:return
        perform {
            val lines=repo.run {call("lines",d,"page" to page).getJSONArray("lines").objects().map {
                TextLine(it.getInt("index"),it.getString("text"),it.getBoolean("editable"))
            }}
            _ui.update {it.copy(lines=lines)}
        }
    }
    fun loadFields() {
        val d=_ui.value.doc?:return
        perform {val fields=repo.run {call("fields",d).formFields()};_ui.update {it.copy(fields=fields)}}
    }
    fun setField(name:String,value:Any?) {
        val d=_ui.value.doc?:return
        perform {
            val result=repo.run {call("set_field",d,"name" to name,"value" to value)}
            publish(result)
            val fields=repo.run {call("fields",_ui.value.doc).formFields()}
            _ui.update {it.copy(fields=fields)}
        }
    }
    suspend fun render(d:Document,page:Int,width:Int):Bitmap=repo.run {render(d,page,width)}
    fun search(query:String) {
        searchJob?.cancel()
        _ui.update {it.copy(query=query,hits=emptyList(),selectedHit=-1,searching=false)}
        val d=_ui.value.doc?:return
        if(query.isBlank())return
        searchJob=viewModelScope.launch {
            delay(180)
            _ui.update {it.copy(searching=true)}
            try {
                for(page in d.pages.indices) {
                    coroutineContext.ensureActive()
                    val results=repo.run {call("search_page",d,"page" to page,"query" to query).getJSONArray("hits").objects().map {h ->
                        val rs=h.getJSONArray("rects")
                        Hit(h.getInt("page"),h.getInt("start"),(0 until rs.length()).map {rs.getJSONArray(it).rect()})
                    }}
                    coroutineContext.ensureActive()
                    if(_ui.value.doc?.generation!=d.generation || _ui.value.doc?.handle!=d.handle)return@launch
                    _ui.update {it.copy(hits=it.hits+results)}
                    // Let rendering and edits use the worker between page searches.
                    delay(1)
                    if(_ui.value.hits.size>=10_000){_ui.update {it.copy(message="Search stopped at 10,000 matches; refine the query.")};break}
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){_ui.update {it.copy(error="Search failed: ${e.message}")}}
            finally {if(_ui.value.query==query)_ui.update {it.copy(searching=false)}}
        }
    }
    fun navigateHit(delta:Int) {
        _ui.update {s -> if(s.hits.isEmpty())s else s.copy(selectedHit=if(s.selectedHit<0)0 else (s.selectedHit+delta+s.hits.size)%s.hits.size)}
    }
    override fun onCleared() {
        val d=_ui.value.doc
        // A separate scope allows native close after viewModelScope has been cancelled.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repo.run {d?.let {call("close",it)};clearCache()}
        }
        super.onCleared()
    }
}
