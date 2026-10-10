package io.github.issisx.alienflow

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

class AlienApp : Application() {
    lateinit var store: LocalStore; private set
    lateinit var coordinator: DictationCoordinator; private set
    override fun onCreate() {
        super.onCreate(); store = LocalStore(this); coordinator = DictationCoordinator(this, store)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) coordinator.screenUnavailable()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
    }
    fun screenReady() = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && !getSystemService(KeyguardManager::class.java).isDeviceLocked
}
