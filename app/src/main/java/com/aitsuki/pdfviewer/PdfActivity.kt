package com.aitsuki.pdfviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.aitsuki.pdfviewer.compose.PDFViewer
import com.aitsuki.pdfviewer.ui.theme.PdfviewerTheme

class PdfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfviewerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PDFViewer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        url = "https://api.idocv.com/data/test/2023/0922/21/215602_1037899_tERZLpf.pdf"
                    )
                }
            }
        }
    }
}