package com.example.mycamera2

import android.opengl.GLES11Ext
import android.opengl.GLES30

class TextureHandler {

    val texture: Int

    init {
        texture = createTexture()
        loadTexture()
    }

    private fun createTexture(): Int {
        val textureHandle = IntArray(1)
        GLES30.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] == 0) {
            throw RuntimeException("Error creating texture.")
        }
        return textureHandle[0]
    }

    private fun loadTexture() {
        if (texture != 0) {
            // Bind to the texture in OpenGL
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)

            // Set filtering
            GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR
            )
            GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR
            )
        }
    }

    fun cleanup() {
        val toIDs = IntArray(1)
        toIDs[0] = texture
        GLES30.glDeleteTextures(1, toIDs, 0)
    }
}