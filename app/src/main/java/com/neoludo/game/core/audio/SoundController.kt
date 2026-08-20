package com.neoludo.game.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.neoludo.game.R

enum class SoundEffect {
    BUTTON_CLICK,
    PIECE_STEP,
    DICE_ROLL,
    PIECE_CAPTURE,
    SAFE_ZONE,
    HOME_ENTER,
    TURN_NOTIFY,
    VICTORY
}

class SoundController(private val context: Context) {
    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<SoundEffect, Int>()
    private var isLoaded = false
    var soundVolume: Float = 1.0f
    var soundEnabled: Boolean = true

    val isSoundEnabled: Boolean get() = soundEnabled

    fun toggleSound(): Boolean {
        soundEnabled = !soundEnabled
        return soundEnabled
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        loadSounds()
    }

    private fun loadSounds() {
        try {
            soundMap[SoundEffect.BUTTON_CLICK] = soundPool.load(context, R.raw.button_click, 1)
            soundMap[SoundEffect.PIECE_STEP] = soundPool.load(context, R.raw.piece_step, 1)
            soundMap[SoundEffect.DICE_ROLL] = soundPool.load(context, R.raw.dice_roll, 1)
            soundMap[SoundEffect.PIECE_CAPTURE] = soundPool.load(context, R.raw.piece_capture, 1)
            soundMap[SoundEffect.SAFE_ZONE] = soundPool.load(context, R.raw.safe_zone, 1)
            soundMap[SoundEffect.HOME_ENTER] = soundPool.load(context, R.raw.home_enter, 1)
            soundMap[SoundEffect.TURN_NOTIFY] = soundPool.load(context, R.raw.turn_notify, 1)
            soundMap[SoundEffect.VICTORY] = soundPool.load(context, R.raw.victory, 1)
            isLoaded = true
        } catch (e: Exception) {
            Log.e("SoundController", "Error loading sounds", e)
        }
    }

    fun play(effect: SoundEffect, volumeMultiplier: Float = 1.0f) {
        if (!soundEnabled || soundVolume <= 0f) return
        val soundId = soundMap[effect] ?: return
        val effectiveVolume = (soundVolume * volumeMultiplier).coerceIn(0f, 1f)
        try {
            soundPool.play(soundId, effectiveVolume, effectiveVolume, 1, 0, 1.0f)
        } catch (e: Exception) {
            Log.e("SoundController", "Error playing sound: $effect", e)
        }
    }

    fun release() {
        soundPool.release()
    }
}
