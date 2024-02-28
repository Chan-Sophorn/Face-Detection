package com.example.facedetect

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

fun Context.isPermissionGranted(cameraPermission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED
}

inline fun Context.cameraPermissionRequest(crossinline positive: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle("Camera Permission Required")
        .setMessage("You cannot scan. Please allow camera permission.")
        .setPositiveButton("Allow camera") { _, _ ->
            positive.invoke()
        }.setNegativeButton("Cancel") { _,_ ->

        }.show()

}

fun Context.openPermissionSetting() {
    Intent("ACTION_PERMISSION_DETAILS_SETTING").also {
        val uri: Uri = Uri.fromParts("package", packageName,null)
        it.data = uri
        startActivity(it)
    }
}