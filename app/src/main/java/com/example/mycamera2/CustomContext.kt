package com.example.mycamera2

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.*
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class CustomContext(imageWidth: Int, imageHeight: Int) : OnFrameAvailableListener,
    ObserverSubject<CustomContextObserver?> {
    private var mCtx: EGLContext? = null
    private var mDpy: EGLDisplay? = null
    private var mSurf: EGLSurface? = null
    private var mTextureHandler: TextureHandler? = null
    private var mRenderer: Renderer? = null
    private val mImageWidth: Int
    private val mImageHeight: Int
    private var mSurfaceTexture: SurfaceTexture? = null
    var surface: Surface? = null
        private set
    private val mTransformMatrix = FloatArray(16)
    val syncWithDecoder = Object()
    var frameRendered = false
    val syncWithEncoder = Object()
    var frameEncoded = true
    var frameTime: Long = 0
    private val mObservers: MutableList<WeakReference<CustomContextObserver>> = ArrayList()

    suspend fun setupRenderingContext(context: Context?, encoderInputSurface: Surface) {
        Log.e("Tanya", coroutineContext.toString())
        createEGLContext(encoderInputSurface)
        mTextureHandler = TextureHandler()
        mRenderer = Renderer(context)
        mSurfaceTexture = SurfaceTexture(mTextureHandler!!.texture)
        surface = Surface(mSurfaceTexture)
        setAvailableListener()
        notifySetupComplete()
    }

    private suspend fun setAvailableListener() {
        suspendCancellableCoroutine<Boolean> { con ->
            mSurfaceTexture!!.setOnFrameAvailableListener {
                GlobalScope.launch {
                    frameEncoded = false
                    EGLExt.eglPresentationTimeANDROID(
                        mDpy, mSurf,
                        frameTime * 1000
                    )
                    it.updateTexImage()
                    onDrawFrame()
                    swapSurfaces()
                    frameRendered = true
                    con.resume(true)
                }

            }

        }

    }

    private fun createEGLContext(encoderInputSurface: Surface) {
        mDpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(mDpy, version, 0, version, 1)
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
            mDpy, configAttr, 0,
            configs, 0, 1, numConfig, 0
        )
        val config = configs[0]
        val surfAttr = intArrayOf(
            EGL14.EGL_NONE
        )
        mSurf = EGL14.eglCreateWindowSurface(mDpy, config, encoderInputSurface, surfAttr, 0)
        val ctxAttrib = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        mCtx = EGL14.eglCreateContext(mDpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
        EGL14.eglMakeCurrent(mDpy, mSurf, mSurf, mCtx)
        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (mRenderer != null) {
            mRenderer!!.onDrawFrame(
                mTransformMatrix,
                mTextureHandler!!.texture,
                mImageWidth,
                mImageHeight
            )
        }
    }

    fun release() {
        cleanup()
        mTextureHandler!!.cleanup()
        mSurfaceTexture!!.release()
        EGL14.eglMakeCurrent(
            mDpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroySurface(mDpy, mSurf)
        EGL14.eglDestroyContext(mDpy, mCtx)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(mDpy)
    }

    private fun cleanup() {
        if (mRenderer != null) mRenderer!!.cleanup()
        mRenderer = null
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {

    }

    private fun swapSurfaces() {
        //EGLExt.eglPresentationTimeANDROID(mDpy, mSurf,
        //      bufferInfo.presentationTimeUs * 1000)
        EGL14.eglSwapBuffers(mDpy, mSurf)
    }

    private fun findWeakReference(observer: CustomContextObserver): WeakReference<CustomContextObserver>? {
        var weakReference: WeakReference<CustomContextObserver>? = null
        for (ref in mObservers) {
            if (ref.get() === observer) {
                weakReference = ref
            }
        }
        return weakReference
    }

    override fun registerObserver(observer: CustomContextObserver?) {
        if (observer != null) {
            val weakReference = findWeakReference(observer)
            if (weakReference == null) mObservers.add(WeakReference(observer))
        }
    }

    override fun removeObserver(observer: CustomContextObserver?) {
        if (observer != null) {
            val weakReference = findWeakReference(observer)
            if (weakReference != null) {
                mObservers.remove(weakReference)
            }
        }
    }

    private fun notifySetupComplete() {
        for (co in mObservers) {
            val observer = co.get()
            observer?.setupComplete()
        }
    }

    companion object {
        private val TAG = CustomContext::class.java.simpleName
    }

    init {
        Matrix.setIdentityM(mTransformMatrix, 0)
        mImageWidth = imageWidth
        mImageHeight = imageHeight
    }
}