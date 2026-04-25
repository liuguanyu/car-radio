package com.carradio.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import com.carradio.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Splash screen activity that loads initial data
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var logoImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var loadingTextView: TextView
    private lateinit var progressBar: ProgressBar

    private val repository: RadioRepository by lazy {
        val database = RadioDatabase.getDatabase(this)
        RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logoImageView = findViewById(R.id.logoImageView)
        titleTextView = findViewById(R.id.titleTextView)
        loadingTextView = findViewById(R.id.loadingTextView)
        progressBar = findViewById(R.id.progressBar)

        startAnimations()
        loadInitialData()
    }

    private fun startAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logoImageView.startAnimation(fadeIn)
        titleTextView.startAnimation(fadeIn)

        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        loadingTextView.startAnimation(slideUp)
        progressBar.startAnimation(slideUp)
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val provincesDeferred = lifecycleScope.launch {
                    repository.refreshProvinces()
                }

                val stationsDeferred = lifecycleScope.launch {
                    repository.refreshStations()
                }

                provincesDeferred.join()
                stationsDeferred.join()

                val lastStation = repository.getLastPlayedStation()
                navigateToNextScreen(lastStation)
            } catch (_: Exception) {
                navigateToNextScreen(null)
            }
        }
    }

    private fun navigateToNextScreen(lastPlayedStation: Station?) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                lastPlayedStation?.let {
                    putExtra(MainActivity.EXTRA_LAST_PLAYED_STATION, it as java.io.Serializable)
                }
            }
            startActivity(intent)
            finish()
        }, 1500)
    }
}