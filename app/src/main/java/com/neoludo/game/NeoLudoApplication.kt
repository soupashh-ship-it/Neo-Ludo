package com.neoludo.game

import android.app.Application
import com.neoludo.game.core.audio.HapticController
import com.neoludo.game.core.audio.SoundController
import com.neoludo.game.data.datastore.PreferencesDataStore
import com.neoludo.game.data.repository.FriendRepository
import com.neoludo.game.data.repository.ProfileRepository
import com.neoludo.game.data.repository.SettingsRepository
import com.neoludo.game.data.repository.StatsRepository

class NeoLudoApplication : Application() {

    lateinit var preferencesDataStore: PreferencesDataStore
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var friendRepository: FriendRepository
        private set
    lateinit var soundController: SoundController
        private set
    lateinit var hapticController: HapticController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesDataStore = PreferencesDataStore(this)
        settingsRepository = SettingsRepository(preferencesDataStore)
        profileRepository = ProfileRepository(preferencesDataStore)
        statsRepository = StatsRepository(preferencesDataStore)
        friendRepository = FriendRepository()

        soundController = SoundController(this)
        hapticController = HapticController(this)
    }

    override fun onTerminate() {
        soundController.release()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: NeoLudoApplication
            private set
    }
}
