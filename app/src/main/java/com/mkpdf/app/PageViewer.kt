package com.mkpdf.app

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageViewer(doc:Document,pageIndex:Int,hits:List<Hit>,active:Hit?,fields:List<FormField>,vm:EditorViewModel,onField:(FormField)->Unit) {
    val page=doc.pages[pageIndex]
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var zoom by remember(doc.handle,doc.generation,pageIndex) {mutableFloatStateOf(1f)}
    var pan by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf(Offset.Zero)}
    val fit=if(viewport.width==0)1f else min(viewport.width/page.width,viewport.height/page.height)
    val pw=page.width*fit;val ph=page.height*fit
    fun bound(offset:Offset,z:Float):Offset {
        val x=max(0f,(pw*z-viewport.width)/2);val y=max(0f,(ph*z-viewport.height)/2)
        return Offset(offset.x.coerceIn(-x,x),offset.y.coerceIn(-y,y))
    }
    val transform=rememberTransformableState {factor,delta,_ ->
        val next=(zoom*factor).coerceIn(1f,6f)
        pan=bound(pan*(next/zoom)+delta,next);zoom=next
    }
    var bitmap by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf<Bitmap?>(null)}
    var error by remember(doc.handle,doc.generation,pageIndex) {mutableStateOf<String?>(null)}
    val desired=(pw*zoom).toInt().coerceIn(64,4096)
    LaunchedEffect(doc.handle,doc.generation,pageIndex,desired) {
        if(viewport.width==0)return@LaunchedEffect
        if(bitmap!=null)delay(180) // scale immediately, refresh detail after a pinch settles
        try {bitmap=vm.render(doc,pageIndex,desired);error=null}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message}
    }
    // Search navigation brings the match into the visible area at fit scale.
    LaunchedEffect(active?.start,active?.page) {if(active?.page==pageIndex){zoom=1f;pan=Offset.Zero}}
    val density=LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color(0xFFE3E8E6)).onSizeChanged {viewport=it}
        .transformable(transform,canPan={zoom>1.01f}),contentAlignment=Alignment.Center) {
        if(viewport.width>0) {
            Box(Modifier.size(with(density){pw.toDp()},with(density){ph.toDp()})
                .graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y}
                .background(Color.White)
                .pointerInput(doc.handle,doc.generation,pageIndex,fields) {
                    detectTapGestures(onDoubleTap={zoom=if(zoom>1.1f)1f else 2f;pan=Offset.Zero},onTap={pos ->
                        val x=pos.x/size.width*page.width;val y=pos.y/size.height*page.height
                        fields.firstOrNull {f -> f.widgets.any {w-> !w.hidden && w.page==pageIndex &&
                            x>=w.rect.left && x<=w.rect.right && y>=w.rect.top && y<=w.rect.bottom }}?.let(onField)
                    })
                }) {
                bitmap?.let {Image(it.asImageBitmap(),"PDF page ${pageIndex+1}",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)}
                Canvas(Modifier.fillMaxSize()) {
                    val sx=size.width/page.width;val sy=size.height/page.height
                    fun paint(r:PdfRect,color:Color) {drawRect(color,Offset(r.left*sx,r.top*sy),Size((r.right-r.left)*sx,(r.bottom-r.top)*sy))}
                    hits.filter {it.page==pageIndex}.forEach {h ->h.rects.forEach {paint(it,if(h==active)Color(0x88FF8800)else Color(0x66FFE500))}}
                    fields.flatMap {it.widgets}.filter {it.page==pageIndex && !it.hidden}.forEach {paint(it.rect,Color(0x18008877))}
                }
            }
        }
        if(bitmap==null || error!=null)Text(error?:"Rendering…",color=if(error!=null)Color.Red else Color.DarkGray)
    }
}
