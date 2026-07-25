package com.utility.pdf.pdfcompressor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object StorageUtils {

    fun savePdf(
        context: Context,
        fileName: String,
        write: (OutputStream) -> Unit
    ) {

        if (Build.VERSION.SDK_INT >= 29) {

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )!!

            context.contentResolver.openOutputStream(uri)!!.use(write)

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

        } else {

            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ),
                "CompressoPDF"
            )

            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)

            file.outputStream().use(write)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("application/pdf"),
                null
            )
        }
    }
}
