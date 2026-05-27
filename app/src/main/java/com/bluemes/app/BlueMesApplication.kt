package com.bluemes.app

import android.app.Application
import com.bluemes.app.data.local.BlueMesDatabase

class BlueMesApplication : Application() {

    val database: BlueMesDatabase by lazy { BlueMesDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BlueMesApplication
            private set
    }
}
