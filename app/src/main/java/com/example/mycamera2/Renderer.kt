package com.example.mycamera2

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import com.example.mycamera.ui.PlayerFragment
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Renderer(val context: Context, val color: Int) {
    private val quadCoordinates = floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
    private val quadTexCoordinates = floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f)
    private lateinit var textureVertexBuffer: FloatBuffer
    private lateinit var vertexBuffer: FloatBuffer
    private var program: Int = 0
    private var quadPositionParam: Int = 0
    private var quadTexCoordParam: Int = 0
    private var uColor: Int = 0
    private var uTexMatrixLog: Int = 0
    private var vertexShader: String = ""
    private var fragmentShader: String = ""

    init {
        parseShaders()
        createProgram()
        createTextureVertexBuffer()
        createVertexBuffer()
    }

    private fun parseShaders() {
        vertexShader = loadShaderFile(VERTEX_SHADER_NAME)
        fragmentShader = loadShaderFile(chooseFragmentShader())
    }

    fun cleanup() {
        GLES30.glDeleteProgram(program)
    }

    fun onDrawFrame(
        transformMatrix: FloatArray?,
        texture: Int,
        viewPortWidth: Int,
        viewPortHeight: Int
    ) {
        GLES30.glViewport(0, 0, viewPortWidth, viewPortHeight)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES30.glUniformMatrix4fv(uTexMatrixLog, 1, false, transformMatrix, 0)
        val COORDS_PER_VERTEX = 2
        GLES30.glVertexAttribPointer(
            quadPositionParam,
            COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            COORDS_PER_VERTEX * SIZEOF_FLOAT,
            vertexBuffer
        )
        GLES20.glUniform3f(uColor, 1f, 0f, 0f)
        GLES30.glVertexAttribPointer(
            quadTexCoordParam,
            COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            COORDS_PER_VERTEX * SIZEOF_FLOAT,
            textureVertexBuffer
        )
        GLES30.glEnableVertexAttribArray(quadPositionParam)
        GLES30.glEnableVertexAttribArray(quadTexCoordParam)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(quadPositionParam)
        GLES30.glDisableVertexAttribArray(quadTexCoordParam)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glUseProgram(0)
        checkGLError(TAG, "Draw")
    }

    private fun checkGLError(tag: String, label: String) {
        var lastError = GLES30.GL_NO_ERROR
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(tag, "$label: glError $error")
            lastError = error
        }
        if (lastError != GLES30.GL_NO_ERROR) {
            throw java.lang.RuntimeException("$label: glError $lastError")
        }
    }

    private fun createProgram() {
        val vertexShader: Int = loadGLShader(vertexShader, GLES30.GL_VERTEX_SHADER)
        val fragmentShader: Int = loadGLShader(fragmentShader, GLES30.GL_FRAGMENT_SHADER)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        GLES30.glUseProgram(program)

        quadPositionParam = GLES30.glGetAttribLocation(program, "a_Position")
        quadTexCoordParam = GLES30.glGetAttribLocation(program, "a_TexCoord")
        uTexMatrixLog = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
    }

    private fun createVertexBuffer() {
        val bbVertices =
            ByteBuffer.allocateDirect(quadCoordinates.size * SIZEOF_FLOAT)
        bbVertices.order(ByteOrder.nativeOrder())
        vertexBuffer = bbVertices.asFloatBuffer()
        vertexBuffer.apply {
            put(quadCoordinates)
            position(0)
        }
    }

    private fun loadGLShader(shaderCode: String, type: Int): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        val result = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, result, 0)
        if (result[0] == 0) {
            Log.e(
                TAG,
                "Error compiling shader: " + GLES30.glGetShaderInfoLog(shader)
            )
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Error creating shader.")
        }

        return shader
    }

    private fun createTextureVertexBuffer() {
        val coordsPerVertex = 2
        val bb = ByteBuffer.allocateDirect(
            4 * coordsPerVertex * SIZEOF_FLOAT
        )
        bb.order(ByteOrder.nativeOrder())
        textureVertexBuffer = bb.asFloatBuffer()
        textureVertexBuffer.apply {
            put(quadTexCoordinates)
            position(0)
        }
    }

    private fun loadShaderFile(file: String): String {
        var inputStream: InputStream? = null
        var builder: StringBuilder? = null
        try {
            inputStream = context.assets.open(file)
            val inputBuffer = inputStream.bufferedReader()
            val listLines = inputBuffer.readLines()
            builder = StringBuilder()
            for (line in listLines) {
                builder.append(line)
                builder.append("\r\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
        }

        return builder.toString()
    }

    private fun chooseFragmentShader(): String {
        return when (color) {
            PlayerFragment.NEGATIVE -> FRAGMENT_SHADER_GRAY
            PlayerFragment.BLUE -> FRAGMENT_SHADER_BLUE
            PlayerFragment.RED -> FRAGMENT_SHADER_NEGATIVE
            else -> FRAGMENT_SHADER
        }
    }

    companion object {
        const val TAG = "Renderer"
        const val VERTEX_SHADER_NAME = "shader.vert"
        const val FRAGMENT_SHADER_NEGATIVE = "negative.frag"
        const val FRAGMENT_SHADER_BLUE = "blue.frag"
        const val FRAGMENT_SHADER = "identity.frag"
        const val FRAGMENT_SHADER_GRAY = "gray.frag"
        const val SIZEOF_FLOAT = 4
    }
}