package com.aitsuki.pdfviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aitsuki.pdfviewer.router.AppRouter
import com.aitsuki.pdfviewer.ui.theme.PdfviewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfviewerTheme {
                AppRouter()
            }
        }
    }
}