package com.neoludo.game.core.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticType {
    LIGHT_TICK,
    MEDIUM_CLICK,
    HEAVY_IMPACT,
    SUCCESS_DOUBLE,
    VICTORY_PULSE
}

class HapticController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var hapticsEnabled: Boolean = true

    fun perform(type: HapticType) {
        if (!hapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    HapticType.LIGHT_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticType.MEDIUM_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticType.HEAVY_IMPACT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticType.SUCCESS_DOUBLE -> VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 80, 45),
                        intArrayOf(0, 180, 0, 255),
                        -1
                    )
                    HapticType.VICTORY_PULSE -> VibrationEffect.createWaveform(
                        longArrayOf(0, 50, 70, 70, 70, 120),
                        intArrayOf(0, 150, 0, 200, 0, 255),
                        -1
                    )
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (timings, amplitudes) = when (type) {
                    HapticType.LIGHT_TICK -> longArrayOf(0, 15) to intArrayOf(0, 120)
                    HapticType.MEDIUM_CLICK -> longArrayOf(0, 30) to intArrayOf(0, 200)
                    HapticType.HEAVY_IMPACT -> longArrayOf(0, 50) to intArrayOf(0, 255)
                    HapticType.SUCCESS_DOUBLE -> longArrayOf(0, 30, 60, 40) to intArrayOf(0, 150, 0, 250)
                    HapticType.VICTORY_PULSE -> longArrayOf(0, 50, 70, 70, 70, 120) to intArrayOf(0, 150, 0, 200, 0, 255)
                }
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    HapticType.LIGHT_TICK -> vibrator.vibrate(15)
                    HapticType.MEDIUM_CLICK -> vibrator.vibrate(30)
                    HapticType.HEAVY_IMPACT -> vibrator.vibrate(60)
                    HapticType.SUCCESS_DOUBLE -> vibrator.vibrate(longArrayOf(0, 30, 60, 40), -1)
                    HapticType.VICTORY_PULSE -> vibrator.vibrate(longArrayOf(0, 50, 70, 70, 70, 120), -1)
                }
            }
        } catch (e: Exception) {
            // Ignored if device lacks granular vibration support
        }
    }
}
