package com.soumyacoudhury.randommusings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var pongView: PongView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContentView(R.layout.activity_main)
        pongView = findViewById(R.id.pongView)
    }

    override fun onResume() {
        super.onResume()
        pongView.resume()
    }

    override fun onPause() {
        pongView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        pongView.shutdown()
        super.onDestroy()
    }
}
