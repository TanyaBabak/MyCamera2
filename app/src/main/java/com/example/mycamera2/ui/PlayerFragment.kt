package com.example.mycamera.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.example.mycamera.constant.BundleConst
import com.example.mycamera2.FrameProcessor
import com.example.mycamera2.databinding.FragmentPlayerBinding

class PlayerFragment : Fragment() {

    private lateinit var binding: FragmentPlayerBinding
    private lateinit var file: String

    private lateinit var viewModel: FrameProcessor
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        file = arguments?.getString(BundleConst.FILE_NAME) ?: ""
        progressBar = binding.processingBar
        videoView = binding.glView
        videoView.setVideoPath(file)
        videoView.start()
        viewModel = FrameProcessor()
        viewModel.finishCodecLiveData.observe(viewLifecycleOwner, {
            progressBar.visibility = View.GONE
            videoView.setVideoPath(it.absolutePath)
            videoView.start()
        })
        videoView.setOnCompletionListener {
            it.start()
        }


        binding.negativeBtn.setOnClickListener {
            startFilter(RED)
        }
        binding.grayBtn.setOnClickListener {
            startFilter(NEGATIVE)
        }
        binding.blueBtn.setOnClickListener {
            startFilter(BLUE)
        }
    }

    private fun startFilter(color: Int) {
        progressBar.bringToFront()
        viewModel.instance(file, requireContext(), color)
        progressBar.visibility = View.VISIBLE
    }

    companion object {
        const val RED = 2
        const val NEGATIVE = 1
        const val BLUE = 3
    }
}