package com.example.mycamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.*
import android.util.Range
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.mycamera.constant.BundleConst
import com.example.mycamera2.R
import com.example.mycamera2.databinding.FragmentCameraBinding
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume


@RequiresApi(Build.VERSION_CODES.M)
class CameraFragment : Fragment() {
    private var binding: FragmentCameraBinding? = null

    private lateinit var cameraId: String
    private lateinit var surfaceView: SurfaceView
    private lateinit var session: CameraCaptureSession
    private lateinit var camera: CameraDevice
    private var isPressed = true

    private var cameraSizeWidth: Int = 0
    private var cameraSizeHeight: Int = 0
    private var cameraFps = 0


    private val cameraManager: CameraManager by lazy {
        requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val codecSurface: Surface by lazy {
        val mEncoderSurface = MediaCodec.createPersistentInputSurface()
        createMediaRecorder(mEncoderSurface).apply {
            prepare()
            release()
        }
        mEncoderSurface
    }

    private val recorder: MediaRecorder by lazy {
        createMediaRecorder(codecSurface)
    }

    private val outputFile: File by lazy {
        specifyFile()
    }

    private val initRequest: CaptureRequest by lazy {
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surfaceView.holder.surface)
        }.build()
    }

    private val mediaRequest: CaptureRequest by lazy {
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surfaceView.holder.surface)
            addTarget(codecSurface)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(cameraFps, cameraFps))
        }.build()
    }

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding?.root
    }

    @InternalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermissions()
        cameraId = arguments?.getString(BundleConst.ID_CAMERA)!!
        cameraSizeHeight = arguments?.getInt(BundleConst.SIZE_CAMERA_HEIGHT)!!
        cameraSizeWidth = arguments?.getInt(BundleConst.SIZE_CAMERA_WIDTH)!!
        cameraFps = arguments?.getInt(BundleConst.FPS_CAMERA)!!
        surfaceView = binding!!.surfaceView

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                initCamera()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

        })

    }

    @InternalCoroutinesApi
    private fun initCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = createCamera()
        session = createSession()
        session.setRepeatingRequest(initRequest, null, cameraHandler)
        binding!!.ibRecordVideo.setOnClickListener {
            if (isPressed) {
                lifecycleScope.launch(Dispatchers.IO) {
                    session.setRepeatingRequest(mediaRequest, null, cameraHandler)
                    recorder.apply {
                        prepare()
                        start()
                    }
                    isPressed = !isPressed
                }
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(requireActivity(), " остановили стрим", Toast.LENGTH_SHORT)
                        .show()
                    stopStreamVideo()
                    recorder.apply {
                        stop()
                    }
                    isPressed = !isPressed
                    navigate()
                }
            }
        }

    }

    fun stopStreamVideo() {
        try {
            session.stopRepeating()
            session.abortCaptures()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        camera.close()
    }

    suspend fun navigate() {
        withContext(Dispatchers.Main) {
            val bundle = bundleOf(BundleConst.FILE_NAME to outputFile.absolutePath)
            Navigation.findNavController(requireActivity(), R.id.fragment_camera_graph)
                .navigate(
                    R.id.action_cameraFragment_to_playerFragment, bundle)
        }
    }

    @InternalCoroutinesApi
    private suspend fun createSession() = suspendCancellableCoroutine<CameraCaptureSession> {
        camera.createCaptureSession(
            listOf(surfaceView.holder.surface, codecSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    it.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val exception =
                        RuntimeException("Session device ${session.device.id} failure")
                    it.tryResumeWithException(exception)
                }

            },
            cameraHandler
        )
    }

    @InternalCoroutinesApi
    private suspend fun createCamera() = suspendCancellableCoroutine<CameraDevice> {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) = it.resume(camera)

                override fun onDisconnected(camera: CameraDevice) {
                    requireActivity().finish()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    val exception = RuntimeException("Camera ${camera.id} not opened")
                    it.tryResumeWithException(exception)
                }

            }, cameraHandler)
        } catch (e: SecurityException) {

        }


    }

    private fun specifyFile() =
        File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_DCIM),
            "source.mp4"
        )

    private fun createMediaRecorder(surface: Surface) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOrientationHint(ROTATION)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(BIT_RATE)
        if (cameraFps > 0) setVideoFrameRate(cameraFps)
        setVideoSize(cameraSizeWidth, cameraSizeHeight)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Navigation.findNavController(this.requireView()).popBackStack()
        }
    }


    override fun onPause() {
        super.onPause()
        binding = null
        cameraThread.quitSafely()
    }

    companion object {
        const val ROTATION = 90
        const val BIT_RATE = 10000000
        const val TYPE = "video/avc"
        const val TAG = "CameraFragment"
    }

}