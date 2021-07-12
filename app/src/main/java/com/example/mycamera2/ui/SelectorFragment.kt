package com.example.mycamera.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mycamera.adapter.SelectorAdapter
import com.example.mycamera.constant.BundleConst
import com.example.mycamera.data.CameraInfo
import com.example.mycamera2.R
import com.example.mycamera2.databinding.FragmentSelectorBinding

class SelectorFragment : Fragment() {
    private var binding: FragmentSelectorBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSelectorBinding.inflate(inflater, container, false)
        return binding?.root
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val listCameras = getCameraInfo(cameraManager)
        binding!!.rvOptionsCamera.apply {
            layoutManager =
                LinearLayoutManager(requireContext())
            adapter = SelectorAdapter(listCameras) {
                val bundle = bundleOf(
                    BundleConst.ID_CAMERA to it.id,
                    BundleConst.SIZE_CAMERA_WIDTH to it.size.width,
                    BundleConst.SIZE_CAMERA_HEIGHT to it.size.height,
                    BundleConst.FPS_CAMERA to it.fps
                )
                Navigation.findNavController(requireActivity(), R.id.fragment_camera_graph)
                    .navigate(R.id.action_selectorFragment_to_cameraFragment, bundle)
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getCameraInfo(cameraManager: CameraManager): List<CameraInfo> {
        val availableCameras: MutableList<CameraInfo> = mutableListOf()
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val orientation =
                defineOrientation(characteristics.get(CameraCharacteristics.LENS_FACING)!!)
            val capabilities =
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val cameraConfig =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (capabilities!!.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                val targetClass = MediaRecorder::class.java

                // For each size, list the expected FPS
                cameraConfig!!.getOutputSizes(targetClass).forEach { size ->
                    // Get the number of seconds that each frame will take to process
                    val secondsPerFrame =
                        cameraConfig.getOutputMinFrameDuration(targetClass, size) /
                                1_000_000_000.0
                    // Compute the frames per second to let user select a configuration
                    val fps = if (secondsPerFrame > 0) (1.0 / secondsPerFrame).toInt() else 0
                    val fpsLabel = if (fps > 0) "$fps" else "N/A"
                    availableCameras.add(
                        CameraInfo(
                            "$orientation $size $fpsLabel FPS", id, size, fps
                        )
                    )
                }

            }
        }
        return availableCameras
    }

    private fun defineOrientation(value: Int) =
        when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Error"
        }

}