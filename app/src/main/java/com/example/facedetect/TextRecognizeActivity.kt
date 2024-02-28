package com.example.facedetect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TextRecognizeActivity: AppCompatActivity() {
    private val cameraPermission = android.Manifest.permission.CAMERA

    private lateinit var cameraPreview: Preview
    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView
    private lateinit var btnTake: Button
    private lateinit var tvReadText: TextView
    private lateinit var cameraSelector: CameraSelector
    private lateinit var recognizer: TextRecognizer
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var capture: ImageCapture
    private lateinit var size: Size

    lateinit var objectDetector: ObjectDetector
    lateinit var faceDetector: FaceDetector
    lateinit var cameraExecutor: ExecutorService
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.text_recognize_layout)
        previewView = findViewById(R.id.previewView)
        imageView = findViewById(R.id.imageView)
        btnTake = findViewById(R.id.btnTake)
        tvReadText = findViewById(R.id.tvReadText)

        requestCameraAndStartFaceDetection()
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // start camera and face detect
            lunchCamera()
        }
    }
    private fun requestCameraAndStartFaceDetection() {
        if (isPermissionGranted(cameraPermission)) {
            // start camera and face detect
            lunchCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                cameraPermissionRequest(positive = { openPermissionSetting() })
            }
            else -> {
                requestPermissionLauncher.launch(cameraPermission)
            }
        }
    }

    private fun lunchCamera() {
        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                try {
                    processCameraProvider = cameraProviderFuture.get()
                    bindCameraPreview()
                    textRecognizer()
                    objectDetection()
                } catch (e: ExecutionException) {
                    Log.e("OnError", "Unhandled exception", e)
                }
            }, ContextCompat.getMainExecutor(this)
        )
    }

    private fun objectDetection() {
        // Live detection and tracking
        val optionsLiveTracking = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()  // Optional
            .build()

        // Multiple object detection in static images
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()  // Optional
            .build()

        val objectDetector = ObjectDetection.getClient(options)
        imageAnalysis = ImageAnalysis.Builder().setTargetRotation(previewView.display.rotation).build()
        cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) {
            imageProxyObjectDetect(it, objectDetector)
        }
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        } catch (illegalStateException: IllegalStateException) {
            Log.e("faceDetect", illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("faceDetect", illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    private fun imageProxyObjectDetect(imageProxy: ImageProxy, objectDetection: ObjectDetector) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            objectDetection.process(image)
                .addOnSuccessListener { detectedObjects ->
                    // Task completed successfully
//                    for (detect in detectedObjects) {
////                        previewView.controller?.cameraControl?
//                        Log.e("texttttttt", "Object Detection: ${detect.boundingBox}", )
//                    }

                    for (detectedObject in detectedObjects) {
                        val boundingBox = detectedObject.boundingBox
                        val trackingId = detectedObject.trackingId
                        for (label in detectedObject.labels) {
                            val text = label.text
                            if (PredefinedCategory.FOOD == text) {
                                Log.e("texttttttt", "Object Detection: ${text}", )
                            }
                            val index = label.index
                            if (PredefinedCategory.FOOD_INDEX == index) {
                                Log.e("texttttttt", "FooD index Detection: ${index}", )
                            }
                            val confidence = label.confidence
                            tvReadText.text = text
                        }
                    }

                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    Log.e("texttttttt", "Object Detection: ${e.message}", )
                }.addOnCompleteListener{
                    imageProxy.close()
                }
        }

    }

    private fun textRecognizer() {
        // When using Latin script library
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
        size = Size(2000, 2500)
        capture = this.display?.rotation?.let {
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(size)
                .build()
        }!!
        cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) {
            imageProxyTextRecognize(it, recognizer)
        }
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, capture, imageAnalysis)
        } catch (illegalStateException: IllegalStateException) {
            Log.e("faceDetect", illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("faceDetect", illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    private fun imageProxyTextRecognize(imageProxy: ImageProxy, textRecognizer: TextRecognizer) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            imageProxyTextDetect(textRecognizer, image, imageProxy)
        }
    }

    private fun imageProxyTextDetect(textRecognizer: TextRecognizer, inputImage: InputImage, imageProxy: ImageProxy) {
        btnTake.setOnClickListener {
            capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    runOnUiThread {
                        val planeProxy = image.planes[0]
                        val buffer: ByteBuffer = planeProxy.buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val matrix = Matrix()
//                        matrix.postRotate(270f)
//                        matrix.postRotate(360f)
                        imageView.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                        image.close()
                        textRecognizeListener(textRecognizer, inputImage, imageProxy)
                    }

//                    var inImage = inputImage
//                    val mediaImage = image.image
//                    if (mediaImage != null) {
//                        inImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//                    }


                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("texttttttt", "imageCapture Fail: ${exception.message}", )
                }
            })
        }


    }

    private fun textRecognizeListener(textRecognizer: TextRecognizer, inputImage: InputImage, imageProxy: ImageProxy) {
        val result = textRecognizer.process(inputImage).addOnSuccessListener { visionText ->
            // Task completed successfully
            tvReadText.text = visionText.text
            Log.e("texttttttt", "imageProxyTextDetect: $visionText", )

        }.addOnFailureListener { e ->
            Log.e("texttttttt", "imageProxyTextDetect: ${e.message}", )
        }.addOnCompleteListener { imageProxy.close() }
//        tvReadText.text = result.result.text
    }
    private fun bindCameraPreview() {
        cameraPreview = Preview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
        cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview)
        } catch (illegalStateException: IllegalStateException) {
            Log.e("OnError", illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("OnError", illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }


}