package com.utility.pdf.pdfcompressor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimplePdfUi(
    viewModel: PdfViewModel,
    onPickPdf: () -> Unit,
    onCompressPdf: () -> Unit,
    onDownloadPdf: () -> Unit
) {
    val pdfSelected = viewModel.selectedPdfFile.value != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "CompressoPDF",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPickPdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isCompressing.value
        ) {
            Text("Select PDF")
        }

        viewModel.selectedPdfName.value?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selected: $it",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.targetKb.value,
            onValueChange = { viewModel.targetKb.value = it },
            label = { Text("Target size (KB)") },
            enabled = pdfSelected && !viewModel.isCompressing.value,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCompressPdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = pdfSelected &&
                    viewModel.targetKb.value.isNotBlank() &&
                    !viewModel.isCompressing.value
        ) {
            Text(if (viewModel.isCompressing.value) "Compressing..." else "Compress PDF")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDownloadPdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.isCompressed.value
        ) {
            Text("Download Compressed PDF")
        }

        if (viewModel.isCompressing.value) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}
