package com.aitsuki.pdfviewer.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitsuki.pdfviewer.compose.PdfViewer
import com.aitsuki.pdfviewer.util.PdfFileUtils
import java.io.File


@Composable
fun PdfScreen(path: String) {
    val context = LocalContext.current

    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadKey by remember { mutableIntStateOf(0) }
    var pdfFile: File? by remember { mutableStateOf(null) }

    LaunchedEffect(path, downloadKey) {
        if (path.startsWith("http")) {
            try {
                downloadError = false
                downloading = true
                pdfFile = PdfFileUtils.downloadPDF(context, path, { downloadProgress = it })
            } catch (e: Exception) {
                e.printStackTrace()
                downloadError = true
            } finally {
                downloading = false
            }
        } else {
            pdfFile = File(path)
        }
    }

    Scaffold { innerPadding ->
        if (downloading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    progress = { downloadProgress },
                )
                Text(" " + (downloadProgress * 100).toInt().toString() + "%", fontSize = 12.sp)
            }
        } else if (downloadError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { downloadKey += 1 }) {
                    Text("Retry")
                }
            }
        }

        pdfFile?.let {
            PdfViewer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                pdfFile = it
            )
        }
    }
}
