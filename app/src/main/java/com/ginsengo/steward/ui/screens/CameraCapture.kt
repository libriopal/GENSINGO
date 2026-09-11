package com.ginsengo.steward.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.theme.Gen
import java.io.File

/**
 * In-app CameraX capture (PRD Workflow C step 2).
 *
 * The system camera app was the easier option and was rejected: it writes through the
 * device's camera pipeline, which on many phones means the shot also lands in the shared
 * gallery. A patch photo in the gallery is a patch location handed to every app with media
 * permission. Capturing in-process writes exactly one file, into filesDir, and nowhere else.
 */
@Composable
fun CameraCapture(
    targetFile: File,
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            SecondaryAction("Cancel", onCancel, Modifier.align(Alignment.CenterStart),
                accent = Gen.TextSecondary)
            PrimaryAction(
                "Capture",
                {
                    capture(context, imageCapture, targetFile, onCaptured)
                },
                Modifier.align(Alignment.CenterEnd),
            )
        }
        Text(
            "Stored on this phone only",
            style = MaterialTheme.typography.labelSmall,
            color = Gen.TextSecondary,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
        )
    }
}

private fun capture(
    context: Context,
    imageCapture: ImageCapture,
    target: File,
    onCaptured: (File) -> Unit,
) {
    target.parentFile?.mkdirs()
    val options = ImageCapture.OutputFileOptions.Builder(target).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(target)
            override fun onError(exc: ImageCaptureException) = Unit
        },
    )
}
