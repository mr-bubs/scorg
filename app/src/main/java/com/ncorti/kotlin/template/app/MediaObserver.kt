package com.ncorti.kotlin.template.app 

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaObserver(handler: Handler, private val context: Context) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        uri?.let { imageUri ->
            // Increased delay to 750ms. Sometimes OEM skins are slow to write the file to the database.
            CoroutineScope(Dispatchers.Main).launch {
                delay(750) 
                
                try {
                    val cursor = context.contentResolver.query(
                        imageUri, 
                        arrayOf(MediaStore.Images.Media.DATA), 
                        null, null, null
                    )
                    
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val pathColumnIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            val path = c.getString(pathColumnIndex)
                            
                            // DIAGNOSTIC TOAST: Show us exactly what file path the app is seeing
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Scorg sees: $path", Toast.LENGTH_LONG).show()
                            }
                            
                            if (path.lowercase().contains("screenshot")) {
                                Handler(Looper.getMainLooper()).post {
                                    PopupOverlayUI(context).showPopup(path)
                                }
                            }
                        } else {
                            // DIAGNOSTIC TOAST: The file exists, but Android blocked us from reading it
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Scorg error: File empty or locked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // DIAGNOSTIC TOAST: The code physically crashed
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Scorg crash: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

