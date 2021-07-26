package com.example.mycamera2

import android.content.Context
import android.media.*
import android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG
import android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
import android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer

class FrameProcessorViewModel : ViewModel() {

    private lateinit var mediaExtractor: MediaExtractor
    private lateinit var mediaMuxer: MediaMuxer
    private lateinit var renderingContext: CustomContext
    private lateinit var encoder: MediaCodec
    private lateinit var encoderInputSurface: Surface
    private lateinit var context: Context
    private lateinit var decoder: MediaCodec
    private lateinit var mediaFormat: MediaFormat
    private var muxerVideoTrackIndex = -1
    var finishCodecLiveData: MutableLiveData<File> = MutableLiveData()
    private lateinit var outputFile: File


    @InternalCoroutinesApi
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun instance(path: String, context: Context) {
        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)
        this.context = context
        val indexTrack = findIndexTrack()
        if (indexTrack < 0) {
            Log.e(TAG, "track don't find")
            return
        }
        mediaExtractor.selectTrack(indexTrack)
        mediaFormat = mediaExtractor.getTrackFormat(indexTrack)
        val width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
        val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
        outputFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DCIM),
            "output.mp4"
        )
        mediaMuxer = MediaMuxer(
            outputFile.path, MUXER_OUTPUT_MPEG_4
        )
        mediaMuxer.setOrientationHint(rotation)
        renderingContext = CustomContext(width, height)
        launchEncoder(width, height, mime)
    }

    fun findIndexTrack(): Int {
        val count = mediaExtractor.trackCount
        for (index in 0..count) {
            val format = mediaExtractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (format != null && format.startsWith("video/")) {
                return index
            }
        }
        return -1
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createDecoder() {
        Log.e("Tanya", Thread.currentThread().name)
        val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
        decoder = MediaCodec.createDecoderByType(mime ?: return)
        decoder.setCallback(@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.e("Tanya", Thread.currentThread().name);
                val buffer = codec.getInputBuffer(index)
                Log.d(TAG, "Decoder filling buffer: $index")
                fillInputBuffer(buffer, index)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Log.e("Tanya", Thread.currentThread().name)
                Log.d(
                    TAG,
                    "Decoder processing output buffer " + index + " size: " + info.size + " flags:" + info.flags
                )
                if ((info.flags and BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    decoder.releaseOutputBuffer(index, false)
                } else {
                    processOutputBuffer(index, info)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "decodec error")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.e(TAG, "decodec change")
            }

        })
        decoder.run {
            configure(mediaFormat, renderingContext.surface, null, 0)
            start()
        }
    }

    private fun processOutputBuffer(index: Int, info: MediaCodec.BufferInfo) {
        if ((info.flags and BUFFER_FLAG_END_OF_STREAM) != 0) {
            encoder.signalEndOfInputStream()
        }
        Log.d(TAG, "processOutputBuffer $index")
        renderingContext.frameTime = info.presentationTimeUs
        decoder.releaseOutputBuffer(index, true)
//        if (info.size != 0) {
//            synchronized(renderingContext.syncWithDecoder) {
//                try {
//                    while (!renderingContext.frameRendered)
//                        renderingContext.syncWithDecoder.wait()
//                } catch (e: InterruptedException) {
//                    e.printStackTrace()
//                }
//                renderingContext.frameRendered = false
//            }
//        }
        viewModelScope.launch {
            delay(100)
        }
    }

    private fun fillInputBuffer(buffer: ByteBuffer?, index: Int) {
        if (buffer != null) {
            val sampleSize = mediaExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                decoder.queueInputBuffer(index, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM)
            } else {
                decoder.queueInputBuffer(index, 0, sampleSize, mediaExtractor.sampleTime, 0)
                mediaExtractor.advance()
            }
        }
    }

    @InternalCoroutinesApi
    private fun launchEncoder(width: Int, height: Int, mime: String?) {
        viewModelScope.launch(Dispatchers.IO + CoroutineName("codec")) {
            Log.e("Tanya", Thread.currentThread().toString())
            createEncoder(width, height, mime)
            viewModelScope.launch(Dispatchers.IO + CoroutineName("render")) {
                renderingContext.setupRenderingContext(
                    context,
                    encoderInputSurface,
                    ::createDecoder
                )
            }
        }
    }


    @InternalCoroutinesApi
    private fun createEncoder(width: Int, height: Int, mime: String?) {
        if (mime == null) {
            Log.e(TAG, "mime doesn't define")
            return
        }
        val format = MediaFormat.createVideoFormat(mime, width, height)
        format.apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERNAL)
        }
        encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = encoder.createInputSurface()
        setCallbackCodec()
        encoder.start()
    }

    private fun setCallbackCodec() {
        encoder.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.e(TAG, "codec input buffer")
                Log.e("Tanya", Thread.currentThread().name)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Log.d(TAG, "Encoder processing output buffer $index size ${info.size}")
                val outputBuffer = encoder.getOutputBuffer(index)
                mediaMuxer.writeSampleData(muxerVideoTrackIndex, outputBuffer!!, info)
                codec.releaseOutputBuffer(index, false)
                if (info.size == null) {
                    stop()
                    finishCodecLiveData.postValue(outputFile)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "codec error")
                Log.e("Tanya", Thread.currentThread().name)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.e(TAG, "codec change")
                muxerVideoTrackIndex = mediaMuxer.addTrack(format)
                mediaMuxer.start()
            }
        })
    }


    private fun stop() {
        decoder.run {
            stop()
            release()
        }
        encoder.run {
            stop()
            release()
        }

        mediaMuxer.run {
            stop()
            release()
        }
        renderingContext.release()
        mediaExtractor.release()
        encoderInputSurface.release()
    }


    companion object {
        const val TAG = "FrameProcessorViewModel"
        const val BIT_RATE = 200000
        const val FRAME_RATE = 30
        const val I_FRAME_INTERNAL = 5
    }
}