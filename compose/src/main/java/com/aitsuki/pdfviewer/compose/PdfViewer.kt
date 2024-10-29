package com.aitsuki.pdfviewer.compose

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.sqrt

internal const val TAG = "PdfViewer"

private class BitmapCache(maxByteCount: Int) : LruCache<Int, Bitmap>(maxByteCount) {
    override fun sizeOf(key: Int, value: Bitmap): Int {
        return value.allocationByteCount
    }

    override fun entryRemoved(
        evicted: Boolean,
        key: Int?,
        oldValue: Bitmap?,
        newValue: Bitmap?
    ) {
        if (evicted && oldValue != null) {
            Log.d(TAG, "Remove bitmap $key")
            oldValue.recycle()
        }
    }
}

@Composable
fun PdfViewer(
    pdfFile: File,
    modifier: Modifier = Modifier,
    memoryCacheByteCount: Int = 100 * 1024 * 1024, // 100 MB
    minPageWidth: Int = 1080,
    maxPageWidth: Int = 1440,
    pageRatio: Float = sqrt(2f), // A4 ratio
    maxZoom: Float = 3f
) {
    val coroutineScope = rememberCoroutineScope()
    val cache = remember { BitmapCache(memoryCacheByteCount) }
    val renderMutex = remember { Mutex() }
    var renderer: PdfRenderer? by remember { mutableStateOf(null) }

    DisposableEffect(pdfFile) {
        renderer =
            PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY))
        onDispose {
            try {
                renderer?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight

        val listState = rememberLazyListState()
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { position ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val center = Offset(boxWidth / 2f, boxHeight / 2f)
                            val dx = (position.x - center.x) * (maxZoom - 1)
                            val dy = (position.y - center.y) * (maxZoom - 1)
                            scale = maxZoom
                            offset = Offset(-dx, -dy)
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures(true) { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, maxZoom)
                        val width = boxWidth * scale
                        val height = boxHeight * scale
                        val edgeX = (width - boxWidth) / 2
                        val edgeY = (height - boxHeight) / 2
                        val offsetX = (offset.x + panChange.x).coerceIn(-edgeX, edgeX)
                        var offsetY = offset.y

                        val isOverScrollForward = !listState.canScrollForward && panChange.y < 0
                        val isOverScrollBackward = !listState.canScrollBackward && panChange.y > 0
                        if (isOverScrollForward || isOverScrollBackward) {
                            offsetY = (offsetY + panChange.y).coerceIn(-edgeY, edgeY)
                        }

                        offset = Offset(offsetX, offsetY)
                        coroutineScope.launch {
                            if (listState.canScrollBackward) {
                                listState.scrollBy(-panChange.y / scale)
                            } else if (listState.canScrollForward) {
                                listState.scrollBy(panChange.y / scale)
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
            state = listState,
        ) {
            items(renderer?.pageCount ?: 0) { index ->
                Box(
                    modifier = Modifier
                        .size(maxWidth, maxWidth * pageRatio)
                        .background(Color.White),
                ) {
                    var bitmap: Bitmap? by remember(index) { mutableStateOf(cache.get(index)) }
                    LaunchedEffect(index) {
                        renderMutex.withLock {
                            if (bitmap == null) {
                                try {
                                    val width = boxWidth.coerceIn(minPageWidth, maxPageWidth)
                                    val height = (width * pageRatio).toInt()
                                    Log.d(TAG, "Create Bitmap $index, $width x $height")
                                    renderer?.openPage(index)?.use { page ->
                                        val newBitmap = createBitmap(width, height)
                                        page.render(
                                            newBitmap,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )
                                        cache.put(index, newBitmap)
                                        bitmap = newBitmap
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    bitmap?.let {
                        Log.d(TAG, "Load bitmap $index")
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = BitmapPainter(it.asImageBitmap()),
                            contentDescription = "PDF Page $index",
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
        }
    }
}