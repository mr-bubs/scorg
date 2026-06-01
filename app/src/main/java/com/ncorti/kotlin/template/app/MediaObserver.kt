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

    // Notice we removed the 'uri?.let'. We don't care if the OS hides the URI from us anymore.
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        // 1. Prove the app actually woke up!
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Scorg woke up!", Toast.LENGTH_SHORT).show()
        }

        // Give the OS a full second to finish writing the heavy image file to the hard drive
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000) 
            
            try {
                // 2. We ask the database for the ONE most recently added image on the whole device
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC" // Sort by newest first
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
                
                cursor?.use { c ->
                    if (c.moveToFirst()) { // Get the very first item (the newest one)
                        val pathColumnIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val path = c.getString(pathColumnIndex)
                        
                        // Show us what file it found
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Newest file: $path", Toast.LENGTH_LONG).show()
                        }
                        
                        // 3. Is it a screenshot? Trigger the popup!
                        if (path.lowercase().contains("screenshot")) {
                            Handler(Looper.getMainLooper()).post {
                                PopupOverlayUI(context).showPopup(path)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Query crash: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
