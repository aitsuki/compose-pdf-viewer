package com.aitsuki.pdfviewer.screen

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aitsuki.pdfviewer.router.LocalNavController
import com.aitsuki.pdfviewer.router.Routes
import com.aitsuki.pdfviewer.util.PdfFileUtils
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    coroutineScope.launch {
                        try {
                            val pdfFile = PdfFileUtils.copyUriFile(context, uri)
                            if (pdfFile != null) {
                                navController.navigate(Routes.Pdf(pdfFile.absolutePath))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                navController.navigate(Routes.Pdf("https://files.s-r.red/compressed.tracemonkey-pldi-09.pdf"))
            }) {
                Text("Open online pdf")
            }

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.type = "application/pdf"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                filePicker.launch(intent)
            }) {
                Text("Open local pdf")
            }

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                coroutineScope.launch {
                    try {
                        val pdfFile = PdfFileUtils.copyAssetsFile(context, "TLCL-19.01.pdf")
                        navController.navigate(Routes.Pdf(pdfFile.absolutePath))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Open assets pdf")
            }
        }
    }
}