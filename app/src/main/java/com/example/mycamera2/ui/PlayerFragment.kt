package com.example.mycamera.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.mycamera.constant.BundleConst
import com.example.mycamera2.FrameProcessorViewModel
import com.example.mycamera2.databinding.FragmentPlayerBinding

class PlayerFragment : Fragment() {

    private lateinit var binding: FragmentPlayerBinding
    private lateinit var file: String

    private lateinit var viewModel: FrameProcessorViewModel
    private lateinit var progressBar: ProgressBar
    private lateinit var videoView: VideoView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        file = arguments?.getString(BundleConst.FILE_NAME) ?: ""
        progressBar = binding.processingBar
        videoView = binding.glView
        videoView.setVideoPath(file)
        videoView.start()
        viewModel = FrameProcessorViewModel()
        videoView.setOnCompletionListener {
            it.start()
        }

        binding.redBtn.setOnClickListener {
            progressBar.bringToFront()
            viewModel.instance(file, requireContext())
            progressBar.visibility = View.VISIBLE
        }
    }
}