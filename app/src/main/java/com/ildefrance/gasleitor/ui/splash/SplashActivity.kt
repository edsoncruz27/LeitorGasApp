package com.ildefrance.gasleitor.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.ildefrance.gasleitor.databinding.ActivitySplashBinding
import com.ildefrance.gasleitor.ui.date.DateConfirmActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigate to date confirmation after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, DateConfirmActivity::class.java))
            finish()
        }, 5000)
    }
}
