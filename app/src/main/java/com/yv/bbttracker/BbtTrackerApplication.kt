package com.yv.bbttracker

import android.app.Application
import com.yv.bbttracker.app.AppContainer
import com.yv.bbttracker.notification.ReminderNotifications

class BbtTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifications.createChannel(this)
    }
}

