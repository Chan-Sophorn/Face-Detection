package com.example.facedetect.facedetection

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler

fun runClassification(bitmap: Bitmap, imageLabeler: ImageLabeler) {
    val inputImage: InputImage = InputImage.fromBitmap(bitmap, 0)
    imageLabeler.process(inputImage).addOnSuccessListener {imgLab ->
        if (imgLab.size > 0) {
            val builder = StringBuilder()
            for (img: ImageLabel in imgLab) {
                builder.append(img.text)
                    .append(" : ")
                    .append(img.confidence)
                    .append("\n")
            }
            Log.e("onError", "imageLabeler -->: $builder" )
        } else {
            Log.e("onError", "imageLabeler: Could not classify" )
        }
    }.addOnFailureListener { ex ->
        Log.e("onError", "runClassification: ${ex.printStackTrace()}" )
    }

}