package com.rrgmc.peopleface

import android.app.Application
import com.rrgmc.peopleface.data.AppContainer

class PeopleFaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
