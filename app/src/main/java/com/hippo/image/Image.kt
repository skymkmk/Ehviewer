/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.image

import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.hippo.ehviewer.EhApplication
import java.nio.ByteBuffer
import kotlin.math.min

class Image private constructor(source: Source, private var src: ByteBufferSource?) {
    var mObtainedDrawable: Drawable?

    init {
        mObtainedDrawable =
            ImageDecoder.decodeDrawable(source) { decoder: ImageDecoder, info: ImageInfo, _: Source ->
                decoder.setTargetColorSpace(colorSpace)
                decoder.setTargetSampleSize(
                    min(
                        info.size.width / (2 * screenWidth),
                        info.size.height / (2 * screenHeight)
                    ).coerceAtLeast(1)
                )
            }
        if (mObtainedDrawable is BitmapDrawable) {
            src?.close()
            src = null
        }
    }

    val width: Int
        get() = mObtainedDrawable?.intrinsicWidth ?: 0
    val height: Int
        get() = mObtainedDrawable?.intrinsicHeight ?: 0

    @Synchronized
    fun recycle() {
        (mObtainedDrawable as? Animatable)?.run {
            stop()
        }
        (mObtainedDrawable as? BitmapDrawable)?.run { bitmap.recycle() }
        mObtainedDrawable?.callback = null
        mObtainedDrawable = null
        src?.close()
        src = null
    }

    companion object {
        val screenWidth = EhApplication.application.resources.displayMetrics.widthPixels
        val screenHeight = EhApplication.application.resources.displayMetrics.heightPixels
        val isWideColorGamut =
            EhApplication.application.resources.configuration.isScreenWideColorGamut
        var colorSpace = ColorSpace.get(
            if (isWideColorGamut && EhApplication.readerPreferences.wideColorGamut()
                    .get()
            ) ColorSpace.Named.DISPLAY_P3 else ColorSpace.Named.SRGB
        )

        @JvmStatic
        fun decode(src: ByteBufferSource): Image? {
            val directBuffer = src.getByteBuffer()
            check(directBuffer.isDirect)
            rewriteGifSource(directBuffer)
            return runCatching {
                Image(ImageDecoder.createSource(directBuffer), src)
            }.onFailure {
                src.close()
                it.printStackTrace()
            }.getOrNull()
        }

        @JvmStatic
        private external fun rewriteGifSource(buffer: ByteBuffer)
    }

    interface ByteBufferSource : AutoCloseable {
        // A read-write direct bytebuffer, it may in native heap or file mapping
        fun getByteBuffer(): ByteBuffer
    }
}