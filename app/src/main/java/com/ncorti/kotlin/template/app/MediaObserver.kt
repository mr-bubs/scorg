package com.ncorti.kotlin.template.app 


import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaObserver(handler: Handler, private val context: Context) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        uri?.let { imageUri ->
            // Small delay to ensure the file is fully written to disk by the OS
            CoroutineScope(Dispatchers.Main).launch {
                delay(500) 
                
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
                            
                            // Check if the new image is a screenshot
                            if (path.lowercase().contains("screenshot")) {
                                Log.d("Scorg", "Screenshot detected! Path: $path")
                                
                                // Launch the Popup UI on the Main Thread
                                Handler(Looper.getMainLooper()).post {
                                    PopupOverlayUI(context).showPopup(path)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Scorg", "Error reading media path", e)
                }
            }
        }
    }
}
