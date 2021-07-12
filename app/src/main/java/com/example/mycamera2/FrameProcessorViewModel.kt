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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer

class FrameProcessorViewModel : ViewModel(), CustomContextObserver {

    private lateinit var mediaExtractor: MediaExtractor
    private lateinit var mediaMuxer: MediaMuxer
    private lateinit var renderingContext: CustomContext
    private lateinit var encoder: MediaCodec
    private lateinit var encoderInputSurface: Surface
    private lateinit var context: Context
    private lateinit var decoder: MediaCodec
    private lateinit var mediaFormat: MediaFormat
    private val mutex = Mutex()


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
        mediaMuxer = MediaMuxer(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DCIM),
                "output.mp4"
            ).path, MUXER_OUTPUT_MPEG_4
        )
        mediaMuxer.setOrientationHint(rotation)
        renderingContext = CustomContext(width, height)
        renderingContext.registerObserver(this)
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
    override fun setupComplete() {
        createDecoder()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createDecoder() {
        val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
        decoder = MediaCodec.createDecoderByType(mime ?: return)
        decoder.setCallback(@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                val buffer = codec.getInputBuffer(index)
                Log.e(TAG, "decodec input buffer")
                fillInputBuffer(buffer, index)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Log.e(TAG, "decoder buffer $index size ${info.flags}")
                if ((info.flags and BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    decoder.releaseOutputBuffer(index, false)
                } else {
                    passInCoder(index, info)

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

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun passInCoder(index: Int, info: MediaCodec.BufferInfo) {
        if ((info.flags and BUFFER_FLAG_END_OF_STREAM) != 0) {
            encoder.signalEndOfInputStream()
        } else {
            renderingContext.frameTime = info.presentationTimeUs
            decoder.releaseOutputBuffer(index, true)
            if (info.size != 0) {
                Log.e("Tanya", Thread.currentThread().name)
            }
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

    private fun launchEncoder(width: Int, height: Int, mime: String?) {
        viewModelScope.launch (Dispatchers.Default) {
            createEncoder(width, height, mime)
            renderingContext.setupRenderingContext(context, encoderInputSurface)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createEncoder(width: Int, height: Int, mime: String?) {
        if (mime == null) {
            Log.e(TAG, "mime doesn't define")
            return
        }
        Log.e("Tanya", Thread.currentThread().name)
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
        setCallbackCodec()
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = encoder.createInputSurface()
        encoder.start()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setCallbackCodec() {
        encoder.setCallback(@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.e(TAG, "codec input buffer")
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Log.e(TAG, "codec output buffer")
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "codec error")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.e(TAG, "codec change")
            }

        })
    }


    companion object {
        const val TAG = "FrameProcessorViewModel"
        const val BIT_RATE = 200000
        const val FRAME_RATE = 30
        const val I_FRAME_INTERNAL = 5
    }
}