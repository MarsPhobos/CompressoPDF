package com.utility.pdf.pdfcompressor

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import java.io.File

class PdfViewModel : ViewModel() {

    val selectedPdfName = mutableStateOf<String?>(null)
    val selectedPdfFile = mutableStateOf<File?>(null)

    val targetKb = mutableStateOf("")

    val isCompressing = mutableStateOf(false)
    val isCompressed = mutableStateOf(false)

    val compressedPdfFile = mutableStateOf<File?>(null)

    fun resetCompression() {
        isCompressed.value = false
        compressedPdfFile.value = null
    }
}
