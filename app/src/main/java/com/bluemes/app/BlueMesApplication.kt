package com.bluemes.app

import android.app.Application
import com.bluemes.app.bluetooth.BlueMesManager
import com.bluemes.app.data.local.BlueMesDatabase

class BlueMesApplication : Application() {
    val database: BlueMesDatabase by lazy { BlueMesDatabase.getInstance(this) }
    val bluetoothManager: BlueMesManager by lazy { BlueMesManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BlueMesApplication
            private set
    }
}
