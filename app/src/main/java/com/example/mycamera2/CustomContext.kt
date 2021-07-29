package com.example.mycamera2

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface

class CustomContext(imageWidth: Int, imageHeight: Int) {
    private var eglContext: EGLContext? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglSurface: EGLSurface? = null
    private lateinit var textureHandler: TextureHandler
    private lateinit var renderer: Renderer
    private val imageWidth: Int
    private val imageHeight: Int
    private var surfaceTexture: SurfaceTexture? = null
    var surface: Surface? = null
    private val transformMatrix = FloatArray(16)
    private var color: Int = 0
    val objectEncoder = Object()
    val objectDecoder = Object()
    var frameTime: Long = 0
    var frameRendered = false
    var frameEncoded = true

    fun setupRenderingContext(
        context: Context?,
        encoderInputSurface: Surface,
        color: Int,
        setupDecoder: () -> Unit
    ) {
        this.color = color
        createEGLContext(encoderInputSurface)
        textureHandler = TextureHandler()
        renderer = Renderer(context!!, color)
        surfaceTexture = SurfaceTexture(textureHandler.texture)
        surface = Surface(surfaceTexture)
        surfaceTexture!!.setOnFrameAvailableListener {
            synchronized(objectEncoder)
            {
                try {
                    while (!frameEncoded) {
                        objectEncoder.wait()
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                frameEncoded = false
            }
            EGLExt.eglPresentationTimeANDROID(
                eglDisplay, eglSurface,
                frameTime * 1000
            )
            surfaceTexture!!.updateTexImage()
            onDrawFrame()
            swapSurfaces()
            synchronized(objectDecoder)
            {
                frameRendered = true
                objectDecoder.notify()
            }
        }
        setupDecoder.invoke()
    }


    private fun createEGLContext(encoderInputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        val configAttr = intArrayOf(
            EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
            EGL14.EGL_LEVEL, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        EGL14.eglChooseConfig(
            eglDisplay, configAttr, 0,
            configs, 0, 1, numConfig, 0
        )
        val config = configs[0]
        val surfAttr = intArrayOf(
            EGL14.EGL_NONE
        )
        eglSurface =
            EGL14.eglCreateWindowSurface(eglDisplay, config, encoderInputSurface, surfAttr, 0)
        val ctxAttrib = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        renderer.onDrawFrame(
            transformMatrix,
            textureHandler.texture,
            imageWidth,
            imageHeight
        )
    }

    fun release() {
        renderer.cleanup()
        textureHandler.cleanup()
        surfaceTexture!!.release()
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(eglDisplay)
    }

    private fun swapSurfaces() {
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    init {
        Matrix.setIdentityM(transformMatrix, 0)
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
    }
}