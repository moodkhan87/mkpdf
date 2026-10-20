package com.mkpdf.app

import android.graphics.Bitmap
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

internal object RustBridge {
    init { System.loadLibrary("mkpdf_jni") }
    private var owner: Long? = null
    private fun confined() {
        val thread = Thread.currentThread().id
        check(owner == null || owner == thread) { "JNI must stay on the persistent MKpdf worker" }
        owner = thread
    }
    @JvmStatic private external fun nativeCall(request: String): String?
    @JvmStatic private external fun nativeRender(handle: Long, page: Int, width: Int, generation: Long): ByteArray?
    fun call(request: JSONObject): JSONObject {
        confined()
        val envelope = JSONObject(nativeCall(request.toString()) ?: throw IOException("Empty JNI reply"))
        if (!envelope.getBoolean("ok")) throw IOException(envelope.optString("error", "Native operation failed"))
        return envelope.getJSONObject("data")
    }
    fun render(doc: Document, page: Int, width: Int): Bitmap {
        confined()
        val bytes = nativeRender(doc.handle,page,width,doc.generation) ?: throw IOException("Empty native raster")
        if (bytes.size < 8) throw IOException("Truncated raster header")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val w = buffer.int; val h = buffer.int
        if (w <= 0 || h <= 0 || w.toLong()*h > 8_100_000 || 8L+w.toLong()*h*4 != bytes.size.toLong())
            throw IOException("Invalid raster dimensions")
        // Engine pixels are premultiplied RGBA8, matching Android ARGB_8888's native byte layout.
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also {
            it.setPremultiplied(true); it.copyPixelsFromBuffer(buffer.slice())
        }
    }
}
