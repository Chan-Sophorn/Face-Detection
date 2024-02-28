package com.example.facedetect

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.facedetect.facedetection.FaceDetectionActivity
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cameraPermission = android.Manifest.permission.CAMERA

    lateinit var cameraSelector: CameraSelector
    lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    lateinit var processCameraProvider: ProcessCameraProvider
    lateinit var cameraPreview: Preview
    private lateinit var viewCamera: PreviewView
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var btnOpenCamera: Button
    private lateinit var btnTextRecognize: Button
    private lateinit var tvShowValuesQR: TextView
    var isCameraOpen: Boolean = true

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCameraAndScan()
        }

    }
    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewCamera = findViewById(R.id.previewCamera)
        btnOpenCamera = findViewById(R.id.btnOpenCamera)
        tvShowValuesQR = findViewById(R.id.tvShowQR)
        btnTextRecognize = findViewById(R.id.btnTextRecognize)

        findViewById<Button?>(R.id.btnNewScreen).setOnClickListener {
            startActivity(Intent(this, ResultActivity::class.java))
        }
        findViewById<Button?>(R.id.btnFaceDetection).setOnClickListener {
            startActivity(Intent(this, FaceDetectionActivity::class.java))
        }
        btnOpenCamera.setOnClickListener {
            getPermissionCamera()
        }
        btnTextRecognize.setOnClickListener { startActivity(Intent(this, TextRecognizeActivity::class.java)) }

    }


    private fun isPermissionGranted(cameraPermission: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, cameraPermission) != PackageManager.PERMISSION_GRANTED) {
            // Pass any permission you want while launching
            requestPermission.launch(cameraPermission)
            return true
        }
        return false
    }

    private fun getPermissionCamera() {
        if (isPermissionGranted(cameraPermission)) {
            startCameraAndScan()
        } else {
            startCameraAndScan()
        }
    }

    private fun startCameraAndScan() {
        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                processCameraProvider = cameraProviderFuture.get()
                bindCameraPreview()
                bindInputAnalyser()
            }, ContextCompat.getMainExecutor(this)
        )

    }

    private fun bindCameraPreview() {
        cameraPreview = Preview.Builder().setTargetRotation(viewCamera.display.rotation).build()
        cameraPreview.setSurfaceProvider(viewCamera.surfaceProvider)
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview)
        } catch (e: Exception) {
            Log.e("OnError", "bindCameraPreview: ${e.printStackTrace()}", )
        }
    }

    private fun bindInputAnalyser() {
        val cameraControl = LifecycleCameraController(this)
        val barcodeScannerOptions = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)
        imageAnalysis = ImageAnalysis.Builder().setTargetRotation(viewCamera.display.rotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)
        }

//        val imageAnalyzer = ImageAnalysis.Analyzer { image ->  processImageProxy(barcodeScanner, image)}
//        cameraControl.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer)


        try {
            val useCaseGroup = UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(imageAnalysis).build()
            processCameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup)
        } catch (e: Exception) {
            Log.e("OnError", "bindInputAnalyser: ${e.printStackTrace()}", )
        }

    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(barcodeScanner: BarcodeScanner, imageProxy: ImageProxy) {
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(inputImage).addOnSuccessListener { barcodes ->
            if (barcodes.isNotEmpty()) {
                onScan?.invoke(barcodes)
                onScan = null
                barcodes.forEach { barcode ->
                    tvShowValuesQR.text = barcode.rawValue.toString()
                    Log.e("OnError", "processImageProxy: ${barcode.rawValue.toString()}", )
                }
                processCameraProvider.unbindAll()
            }
            barcodes.forEach { barcode ->
                Log.e("OnError", "processImageProxy: ${barcode.rawValue.toString()}", )
                tvShowValuesQR.text = barcode.rawValue.toString()
            }

        }.addOnFailureListener {
            Log.e("OnError", "processImageProxy onFailure: ${it.printStackTrace()}", )
        }.addOnCompleteListener { itComplete ->
            Log.e("OnError", "processImageProxy onCompleted: $", )
            imageProxy.close()
        }
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                cameraPermissionRequest {
                    openPermissionSetting()
                }
            }
            else -> requestPermission.launch(cameraPermission)
        }
    }

    companion object {
        private var onScan: ((barcode: List<Barcode>) -> Unit)? = null
    }

    override fun onResume() {
        super.onResume()
//        bindInputAnalyser()
    }
}