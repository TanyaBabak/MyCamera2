package com.example.mycamera.ui

import android.Manifest.permission.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.mycamera2.BuildConfig
import com.example.mycamera2.R
import com.example.mycamera2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            run {
                for (entry in map.entries) {
                    Log.e("Request", "${entry.key} : ${entry.value}")
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        container = findViewById(R.id.fragment_camera_graph)
        requestPermissionAccess()
    }

    private fun requestPermissionAccess() {
        if (shouldShowRequestPermissionRationale(CAMERA) || shouldShowRequestPermissionRationale(
                WRITE_EXTERNAL_STORAGE
            ) || shouldShowRequestPermissionRationale(RECORD_AUDIO)
        ) {
            forwardToSettings()
        } else {
            requestPermission.launch(arrayOf(CAMERA, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun forwardToSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts(PACKAGE, BuildConfig.APPLICATION_ID, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    companion object {
        private val PACKAGE = "package"
    }


}