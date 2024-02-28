package com.example.facedetect

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.common.Barcode

class ResultActivity : AppCompatActivity() {

    private val cameraPermission = android.Manifest.permission.CAMERA

    private lateinit var btnOpenCamera: Button
    private lateinit var textViewQrType: TextView
    private lateinit var textViewQrContent: TextView

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startScanner()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.result_activity)
        textViewQrContent = findViewById(R.id.text_view_qr_content)
        textViewQrType = findViewById(R.id.text_view_qr_type)
        btnOpenCamera = findViewById(R.id.button_open_scanner)

        btnOpenCamera.setOnClickListener {
            requestCameraAndStartScanner()
        }

    }

    private fun requestCameraAndStartScanner() {
        if (isPermissionGranted(cameraPermission)) {
            startScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                cameraPermissionRequest(
                    positive = { openPermissionSetting() }
                )
            }
            else -> {
                requestPermissionLauncher.launch(cameraPermission)
            }
        }
    }


    private fun startScanner() {
        ScannerActivity.startScanner(this) { barcodes ->
            barcodes.forEach { barcode ->
                when (barcode.valueType) {
                    Barcode.TYPE_URL -> {
                        textViewQrType.text = "URL"
                        textViewQrContent.text = barcode.url.toString()
                    }
                    Barcode.TYPE_CONTACT_INFO -> {
                        textViewQrType.text = "Contact"
                        textViewQrContent.text = barcode.contactInfo.toString()
                    }
                    else -> {
                        textViewQrType.text = "Other"
                        textViewQrContent.text = barcode.rawValue.toString()
                    }
                }
            }
        }
    }


}