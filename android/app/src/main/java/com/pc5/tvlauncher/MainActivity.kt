package com.pc5.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

/** Native Android TV home screen with the PC5 Launcher look. */
class MainActivity : ComponentActivity(), HomeActions {

    private lateinit var repo: AppRepo
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val homeSignal = mutableIntStateOf(0)
    private val loader = Executors.newSingleThreadExecutor()
    private lateinit var sounds: SoundPool
    private val soundIds = HashMap<String, Int>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        repo = AppRepo(this)
        sounds = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .build()
        mapOf("focus" to R.raw.snd_focus, "select" to R.raw.snd_select, "back" to R.raw.snd_back,
            "edge" to R.raw.snd_edge, "launch" to R.raw.snd_launch, "tab" to R.raw.snd_tab)
            .forEach { (k, res) -> soundIds[k] = sounds.load(this, res, 1) }

        // A launcher never closes on Back; the home screen handles Back itself
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })

        androidx.core.content.ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)

        setContent { HomeScreen(apps.value, homeSignal.intValue, this) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        sounds.release()
        loader.shutdown()
        super.onDestroy()
    }

    private fun reload() {
        loader.execute {
            val list = runCatching { repo.load() }.getOrDefault(emptyList())
            runOnUiThread { apps.value = list }
        }
    }

    override fun launch(app: AppEntry) { repo.launch(app) }
    override fun details(app: AppEntry) = repo.openDetails(app)
    override fun settings() = repo.openSettings()
    override fun power() {
        // Apps can't shut the TV down; open the system settings (power options live there)
        runCatching { startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { repo.openSettings() }
    }
    override fun sound(name: String) {
        soundIds[name]?.let { sounds.play(it, 0.6f, 0.6f, 1, 0, 1f) }
    }
}
