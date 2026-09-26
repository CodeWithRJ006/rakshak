package com.rakshak.core.sensor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CameraCaptureHelper {
    suspend fun takePicture(context: Context, lifecycleOwner: LifecycleOwner): Bitmap? = suspendCoroutine { cont ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.capacity())
                                buffer.get(bytes)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                                
                                val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                
                                image.close()
                                cameraProvider.unbindAll()
                                cont.resume(rotatedBitmap)
                            } catch (e: Exception) {
                                image.close()
                                cameraProvider.unbindAll()
                                cont.resume(null)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("CameraCapture", "Capture failed", exception)
                            cameraProvider.unbindAll()
                            cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("CameraCapture", "Camera initialization failed", e)
                cont.resume(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
