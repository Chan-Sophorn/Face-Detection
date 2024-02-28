package com.example.facedetect.facedetection

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.facedetect.R
import com.example.facedetect.cameraPermissionRequest
import com.example.facedetect.isPermissionGranted
import com.example.facedetect.openPermissionSetting
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectionActivity : AppCompatActivity() {

    private val cameraPermission = android.Manifest.permission.CAMERA

    private lateinit var cameraPreview: Preview
    private lateinit var previewView: PreviewView
    private lateinit var imageLeft: ImageView
    private lateinit var imageRight: ImageView
    private lateinit var imageTop: ImageView
    private lateinit var imageBottom: ImageView
    private lateinit var cameraSelector: CameraSelector
    private lateinit var recognizer: TextRecognizer
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var tvDirection: TextView
    private lateinit var capture: ImageCapture
    private lateinit var size: Size

    lateinit var imageLabeler: ImageLabeler
    lateinit var objectDetector: ObjectDetector
    lateinit var faceDetector: FaceDetector
    lateinit var cameraExecutor: ExecutorService


    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // start camera and face detect
            lunchCamera()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.face_detection_activity)
        previewView = findViewById(R.id.previewView)
        imageLeft = findViewById(R.id.imageLeft)
        imageRight = findViewById(R.id.imageRight)
        imageTop = findViewById(R.id.imageTop)
        imageBottom = findViewById(R.id.imageBottom)
        tvDirection = findViewById(R.id.tvDirection)

        requestCameraAndStartFaceDetection()
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
        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                try {
                    processCameraProvider = cameraProviderFuture.get()
                    bindCameraPreview()
                    faceDetect()
                } catch (e: ExecutionException) {
                    Log.e("OnError", "Unhandled exception", e)
                }
            }, ContextCompat.getMainExecutor(this)
        )
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

    private fun objectDetect() {
        val option = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(option)
    }

    private fun faceDetect() {
        // High-accuracy landmark detection and face classification
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()

        // Real-time contour detection
        val realTimeOpts = FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        faceDetector = FaceDetection.getClient(highAccuracyOpts)
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
            processImageProxy(it, faceDetector)
        }
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, capture, imageAnalysis)
        } catch (illegalStateException: IllegalStateException) {
            Log.e("faceDetect", illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("faceDetect", illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, faceDetector: FaceDetector) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            imageProxyDetection(faceDetector, image, imageProxy)
        }
    }

    var isLeft = true
    var isRight = false
    var isTop = false
    var isBottom = false
    var isCenter = true

    private fun imageProxyDetection(faceDetector: FaceDetector, image: InputImage, imageProxy: ImageProxy) {
        faceDetector.process(image).addOnSuccessListener { faces ->
            for (face in faces) {
                val bounds = face.boundingBox
                val rotateY = face.headEulerAngleY // Head is rotated to the right rotateY degrees
                val rotateZ = face.headEulerAngleZ // Head is tilted sideways rotateZ degrees
                val rotateX = face.headEulerAngleX // Head is tilted sideways rotateX degrees


                Log.e("faceDetectXY", "imageProxyDetection: Y-> $rotateY  rotX -> $rotateX  rotZ -> $rotateZ", )

                if(rotateY > 0 && rotateY < 3 && rotateX > 0 && rotateX < 3 && isCenter) {
                    Log.e("Center","Center")
                }
                if(rotateY > 36 && isLeft) {
                    tvDirection.text = "Please move your face to right"
                    onClick(isLeft) {
                        isCenter = false
                        isLeft = it
                        isRight = true
                    }

                }

                if(rotateY < -36 && isRight) {
                    tvDirection.text = "Please move your face up"
                    onClick(isRight) {
                        isLeft = false
                        isRight = it
                        isTop = true
                    }

                }

                if(rotateX > 15 && isTop) {
                    tvDirection.text = "Please move your face down"
                    onClick(isTop) {
                        isRight = false
                        isTop = it
                        isBottom = true
                    }

                }

                if(rotateX < -5 && isBottom) {
                    tvDirection.text = "Verified successfully"
                    onClick(isBottom) {
                        isTop = false
                        isBottom = it
                    }

                }

//                Log.e("ooooooIs", "imageProxyDetection: Center : $isCenter -- Left: $isLeft  -- Right: $isRight -- Top : $isTop -- Bottom : $isBottom", )

                // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                // nose available):
                val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                leftEar?.let {
                    val leftEarPos = leftEar.position
                    Log.e("faceDetect", "leftEar: $leftEarPos", )
                }

                // If contour detection was enabled:
                val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
                val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points

                // If classification was enabled:
                if (face.smilingProbability != null) {
                    val smileProb = face.smilingProbability
                    Log.e("faceDetect", "processImageProxy: $smileProb", )
                }
                if (face.rightEyeOpenProbability != null) {
                    val rightEyeOpenProb = face.rightEyeOpenProbability
                    Log.e("faceDetect", "face.rightEyeOpenProbabilit: $rightEyeOpenProb", )
                }

                // If face tracking was enabled:
                if (face.trackingId != null) {
                    val id = face.trackingId
                    Log.e("faceDetect", "face trackingId: $id", )
                }
            }

        }.addOnFailureListener {
            Log.e("faceDetect", "Error: ${it.printStackTrace()}", )
        }.addOnCompleteListener {
            imageProxy.close()
        }
        Log.e("ooooooIs", "imageProxyDetection: Center : $isCenter -- Left: $isLeft  -- Right: $isRight -- Top : $isTop -- Bottom : $isBottom", )

    }

    private fun onClick(isFace: Boolean, callback: (result: Boolean) -> Unit) {
        val path = this.filesDir
        val letDirectory = File(path, "LET")
        letDirectory.mkdirs()
        val file = File(letDirectory, "image.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(outputFileOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(error: ImageCaptureException)
                {
                    // insert your code here.
                    Log.e("fileName", "onError: ${error.printStackTrace()}", )
                }
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // insert your code here.
                    runOnUiThread {
                        val bitmap = loadFromUri(outputFileResults.savedUri!!)
                        when (isFace) {
                            isLeft -> {
                                imageLeft.setImageBitmap(Bitmap.createBitmap(bitmap))
                                callback(false)
                            }
                            isRight -> {
                                imageRight.setImageBitmap(Bitmap.createBitmap(bitmap))
                                callback(false)
                            }
                            isTop -> {
                                imageTop.setImageBitmap(Bitmap.createBitmap(bitmap))
                                callback(false)
                            }
                            isBottom -> {
                                imageBottom.setImageBitmap(Bitmap.createBitmap(bitmap))
                                callback(false)
                            }
                            else -> ""
                        }
                    }
                }
            })
    }

    private fun verify(face: Face){
        if(face.headEulerAngleY > 0 && face.headEulerAngleY < 3 && face.headEulerAngleX > 0 && face.headEulerAngleX < 3 && tvDirection.text.toString().contains("left")){
            Log.e("oooooooooMove","Center")
        }
        if(face.headEulerAngleY > 40 && tvDirection.text.toString().contains("left")){
            convertFaceToImage("Please move your head to right")
        }

        if(face.headEulerAngleY < -40 && tvDirection.text.toString().contains("right")){
            convertFaceToImage("Please move your head up")
        }

        if(face.headEulerAngleX > 15 && tvDirection.text.toString().contains("up")){
            convertFaceToImage("Please move your head down")
        }

        if(face.headEulerAngleX < -5 && tvDirection.text.toString().contains("down")){

            convertFaceToImage("Verified successfully")
        }
    }

    private fun convertFaceToImage(message:String){
        capture.takePicture(cameraExecutor,object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            val planeProxy = image.planes[0]
                            val buffer: ByteBuffer = planeProxy.buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val matrix = Matrix()
                            matrix.postRotate(270f)
                            // btnCapture.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                            image.close()
                            if(message.contains("right")){
                                imageLeft.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                            }else if(message.contains("up")){
                                imageRight.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                            }else if(message.contains("down")){
                                imageTop.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                            }else{
                                imageBottom.setImageBitmap(Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true))
                            }
                            tvDirection.text = message
                        }
                    }
                }, 50)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("oooooooooMove",exception.message.toString())
            }
        })

    }

    fun loadFromUri(uri: Uri): Bitmap {
        var bitmap: Bitmap? = null

        try {
            bitmap = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                val source: ImageDecoder.Source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("onError", "loadFromUri: ${e.printStackTrace()}", )
        }
        return bitmap!!
    }
}

private class YourImageAnalyzer : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            // ...
        }
    }
}