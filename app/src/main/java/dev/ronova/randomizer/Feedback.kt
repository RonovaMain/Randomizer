package dev.ronova.randomizer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Вибро и звук при вращении. Включаются/выключаются на главном экране. */
class Feedback(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var vibration by mutableStateOf(prefs.getBoolean("vibration", true))
        private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true))
        private set

    fun updateVibration(v: Boolean) {
        vibration = v
        prefs.edit().putBoolean("vibration", v).apply()
    }

    fun updateSound(v: Boolean) {
        sound = v
        prefs.edit().putBoolean("sound", v).apply()
    }

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 40)
    } catch (e: Exception) {
        null
    }

    fun tick() {
        if (vibration) vibrator?.vibrate(VibrationEffect.createOneShot(10, 40))
        if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    fun done() {
        if (vibration) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), -1))
        if (sound) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
    }
}
