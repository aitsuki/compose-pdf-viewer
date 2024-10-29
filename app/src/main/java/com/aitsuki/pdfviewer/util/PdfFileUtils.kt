package com.aitsuki.pdfviewer.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object PdfFileUtils {

    suspend fun copyUriFile(
        context: Context,
        uri: Uri,
    ): File? = withContext(Dispatchers.IO) {
        val hasUri = sha256Url(uri.toString())
        val pdfFile = File(context.cacheDir, "$hasUri.pdf")
        return@withContext context.contentResolver.openInputStream(uri)?.use { inputStream ->
            pdfFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            return@use pdfFile
        }
    }

    suspend fun copyAssetsFile(
        context: Context,
        assetFilename: String
    ): File = withContext(Dispatchers.IO) {
        return@withContext context.assets.open(assetFilename).use { inputStream ->
            val pdfFile = File(context.cacheDir, assetFilename)
            pdfFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            return@use pdfFile
        }
    }

    suspend fun downloadPDF(
        context: Context,
        url: String,
        onProgressChange: (progress: Float) -> Unit,
        cacheEnabled: Boolean = true,
    ): File {
        val hashUrl = sha256Url(url)
        val pdfFile = File(context.cacheDir, "$hashUrl.pdf")
        if (cacheEnabled && pdfFile.exists()) {
            return pdfFile
        }

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

            val tempFile = File(context.cacheDir, "${pdfFile.name}.temp")
            try {
                body.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        val buff = ByteArray(8 * 1024)
                        var downloadLength = 0
                        while (true) {
                            val len = inputStream.read(buff)
                            if (len == -1) break
                            outputStream.write(buff, 0, len)
                            downloadLength += len
                            onProgressChange(downloadLength.toFloat() / contentLength)
                        }
                        tempFile.renameTo(pdfFile)
                    }
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
            return@withContext pdfFile
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun sha256Url(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray())
        return hash.toHexString()
    }
}