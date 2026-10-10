package io.github.issisx.alienflow

import android.app.Application

class AlienApp : Application() {
    lateinit var store: LocalStore; private set
    lateinit var coordinator: DictationCoordinator; private set
    override fun onCreate() {
        super.onCreate(); store = LocalStore(this); coordinator = DictationCoordinator(this, store)
    }
}
