package com.aitsuki.pdfviewer.compose

import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.sqrt

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
@Composable
fun PDFViewer(
    modifier: Modifier = Modifier,
    url: String,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf(false) }
    var downloadKey by remember { mutableIntStateOf(0) }
    val cache: LruCache<Int, Bitmap> = remember {
        //                              50 MB (大概能缓存12页)
        object : LruCache<Int, Bitmap>(50 * 1024 * 1024) {
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
                    Log.d("PDFViewer", "remove bitmap: $key")
                    oldValue.recycle()
                }
            }
        }
    }

    var renderer: PdfRenderer? by remember { mutableStateOf(null) }
    val mutex = remember { Mutex() }

    DisposableEffect(url, downloadKey) {
        var pdfFile: File? = null
        coroutineScope.launch {
            try {
                downloading = true
                downloadError = false
                pdfFile = downloadPDF(context, url) { downloadProgress = it }
                renderer = PdfRenderer(
                    ParcelFileDescriptor.open(
                        pdfFile,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                )
            } catch (e: Exception) {
                downloadError = true
                e.printStackTrace()
            } finally {
                downloading = false
            }
        }
        onDispose {
            try {
                cache.evictAll()
                renderer?.close()
                pdfFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (downloading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                progress = { downloadProgress },
            )
            Text(" " + (downloadProgress * 100).toInt().toString() + "%", fontSize = 12.sp)
        }
    } else if (downloadError) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { downloadKey += 1 }) {
                Text("Retry")
            }
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val imageWidth = maxWidth
            val imageHeight = maxWidth * sqrt(2f)
            val listState = rememberLazyListState()
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tapCenter ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                                val center =
                                    Pair(constraints.maxWidth / 2, constraints.maxHeight / 2)
                                val xDiff = (tapCenter.x - center.first) * scale
                                val yDiff = ((tapCenter.y - center.second) * scale).coerceIn(
                                    minimumValue = -(center.second * 2f),
                                    maximumValue = (center.second * 2f)
                                )
                                offset = Offset(-xDiff, -yDiff)
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures(true) { centroid, pan, zoom, rotation ->
                            val pair = if (pan.y > 0) {
                                if (listState.canScrollBackward) {
                                    Pair(0f, pan.y)
                                } else {
                                    Pair(pan.y, 0f)
                                }
                            } else {
                                if (listState.canScrollForward) {
                                    Pair(0f, pan.y)
                                } else {
                                    Pair(pan.y, 0f)
                                }
                            }
                            val nOffset = if (scale > 1f) {
                                val maxT = (constraints.maxWidth * scale) - constraints.maxWidth
                                val maxY = (constraints.maxHeight * scale) - constraints.maxHeight
                                Offset(
                                    x = (offset.x + pan.x).coerceIn(
                                        minimumValue = (-maxT / 2) * 1.3f,
                                        maximumValue = (maxT / 2) * 1.3f
                                    ),
                                    y = (offset.y + pair.first).coerceIn(
                                        minimumValue = (-maxY / 2),
                                        maximumValue = (maxY / 2)
                                    )
                                )
                            } else {
                                Offset(0f, 0f)
                            }
                            offset = nOffset
                            coroutineScope.launch {
                                listState.scrollBy((-pair.second / scale))
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
                state = listState
            ) {
                items(renderer?.pageCount ?: 0) { index ->
                    Box(
                        modifier = Modifier
                            .size(imageWidth, imageHeight)
                            .background(Color.White),
                    ) {
                        var bitmap: Bitmap? by remember(index) { mutableStateOf(cache.get(index)) }
                        if (bitmap != null) {
                            Log.d("PDFViewer", "Load bitmap $index")
                            bitmap?.let {
                                Image(
                                    modifier = Modifier.fillMaxSize(),
                                    painter = BitmapPainter(it.asImageBitmap()),
                                    contentDescription = "PDF Page $index",
                                    contentScale = ContentScale.FillBounds,
                                )
                            }
                        } else {
                            LaunchedEffect(index) {
                                mutex.withLock {
                                    Log.d("PDFViewer", "Create Bitmap $index")
                                    try {
                                        val imageWidthPx = with(density) {
                                            imageWidth.toPx().toInt().coerceAtMost(1080)
                                        }
                                        renderer?.openPage(index)?.use { page ->
                                            val newBitmap =
                                                createBitmap(
                                                    imageWidthPx,
                                                    imageWidthPx * sqrt(2f).toInt()
                                                )
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
                    }
                }
            }
        }
    }
}

private suspend fun downloadPDF(
    context: Context,
    url: String,
    onProgressChange: (progress: Float) -> Unit,
): File {
    return withContext(Dispatchers.IO) {
        onProgressChange(0f)
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("Download $url failed with status code: ${response.code}")
        }
        val body = response.body ?: error("Download $url failed, body is null")
        val contentLength = body.contentLength()
        if (contentLength < 1) {
            error("Download $url failed, body is empty")
        }
        val pdfFile = File.createTempFile("temp", ".pdf", context.cacheDir)
        body.byteStream().use { inputStream ->
            pdfFile.outputStream().use { outputStream ->
                val buff = ByteArray(8 * 1024)
                var downloadLength = 0
                while (true) {
                    val len = inputStream.read(buff)
                    if (len == -1) break
                    outputStream.write(buff, 0, len)
                    downloadLength += len
                    onProgressChange(downloadLength.toFloat() / contentLength)
                }
            }
        }
        return@withContext pdfFile
    }
}