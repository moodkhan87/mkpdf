package com.mkpdf.app
import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @Test fun nativeLibraryEditsAndRenders()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val repo=PdfRepository(app)
        repo.run {
            init()
            var d=call("blank").document()
            try {
                d=call("add_text",d,"page" to 0,"rect" to listOf(40.0,40.0,300.0,100.0),"size" to 18,"text" to "JNI smoke test").document()
                assertEquals(1,d.pages.size)
                val bitmap=render(d,0,400)
                assertTrue(bitmap.width>0);assertTrue(bitmap.height>0)
                val hits=call("search_page",d,"page" to 0,"query" to "smoke").getJSONArray("hits")
                assertTrue(hits.length()>0)
                val saved=call("snapshot",d)
                assertTrue(java.io.File(saved.getString("path")).length()>0)
            }finally {call("close",d)}
        }
    }
}
