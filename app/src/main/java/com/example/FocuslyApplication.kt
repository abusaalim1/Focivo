package com.example

import android.app.Application
import com.example.data.FirebaseConfigManager
import com.example.data.SupabaseManager
import com.example.service.ScheduledBlockScheduler
import com.example.util.AiStudyGuardManager

class FocuslyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseManager.initialize(this)
        FirebaseConfigManager.initializeFirebase(this)
        AiStudyGuardManager.init(this)
        ScheduledBlockScheduler.evaluateAndReschedule(this)
    }
}

