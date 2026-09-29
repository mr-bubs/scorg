package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView

object BubsMascotFrames {

    enum class Reaction(val index: Int) {
        NEUTRAL(0),
        HAPPY(1),
        EXCITED(2),
        SURPRISED(3),
        THINKING(4),
        ANNOYED(5),
        NERVOUS(6),
        SLEEPY(7),
        PLAYFUL(8)
    }

    private const val GRID = 3

    @Volatile
    private var atlas: Bitmap? = null

    private val frameCache = mutableMapOf<Int, Bitmap>()

    fun setReaction(
        context: Context,
        imageView: ImageView,
        reaction: Reaction
    ) {
        val bitmap = frame(context, reaction) ?: return
        imageView.setImageBitmap(bitmap)
        imageView.contentDescription = "Mr Bubs"
    }

    private fun frame(
        context: Context,
        reaction: Reaction
    ): Bitmap? {
        synchronized(this) {
            frameCache[reaction.index]?.let { return it }

            val source = atlas ?: loadAtlas(context)?.also {
                atlas = it
            } ?: return null

            val tileWidth = source.width / GRID
            val tileHeight = source.height / GRID

            if (tileWidth <= 0 || tileHeight <= 0) return null

            val column = reaction.index % GRID
            val row = reaction.index / GRID

            val left = column * tileWidth
            val top = row * tileHeight

            if (
                left + tileWidth > source.width ||
                top + tileHeight > source.height
            ) {
                return null
            }

            return Bitmap.createBitmap(
                source,
                left,
                top,
                tileWidth,
                tileHeight
            ).also {
                frameCache[reaction.index] = it
            }
        }
    }

    private fun loadAtlas(context: Context): Bitmap? {
        return runCatching {
            context.resources.openRawResource(
                R.drawable.bubs_reactions
            ).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }
}
