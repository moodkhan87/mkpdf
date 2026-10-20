package com.mkpdf.app

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import org.json.JSONObject

/** JNI TLS owns a non-Send Session. The executor must remain alive for the process lifetime. */
private object NativeWorker {
    val dispatcher=Executors.newSingleThreadExecutor { r -> Thread(r,"MKpdf-Rust") }.asCoroutineDispatcher()
    val gate=Mutex()
}
class PdfRepository(application: Application) {
    val saf=SafStore(application)
    private val cache=object:LruCache<String,Bitmap>(32*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    suspend fun <T> run(block:suspend PdfRepository.()->T):T = withContext(NativeWorker.dispatcher) {
        NativeWorker.gate.withLock { block() }
    }
    fun call(op:String, doc:Document?=null, vararg args:Pair<String,Any?>):JSONObject {
        val request=obj("op" to op,*args)
        doc?.let {request.put("handle",it.handle);request.put("generation",it.generation)}
        return RustBridge.call(request)
    }
    fun init() {call("init",null,"root" to saf.root.absolutePath);saf.cleanInputs()}
    fun render(doc:Document,page:Int,width:Int):Bitmap {
        val key="${doc.handle}:${doc.generation}:$page:$width"
        return cache.get(key) ?: RustBridge.render(doc,page,width).also {cache.put(key,it)}
    }
    fun clearCache() = cache.evictAll()
}
