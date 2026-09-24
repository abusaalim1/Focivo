package com.example

import android.app.Application
import android.util.Log
import com.example.data.FirebaseConfigManager
import com.example.data.SupabaseManager
import com.example.service.ScheduledBlockScheduler
import com.example.util.AiStudyGuardManager
import com.example.util.AppDailyLimitsManager

class FocuslyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            SupabaseManager.initialize(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize SupabaseManager: ${e.message}", e)
        }

        try {
            FirebaseConfigManager.initializeFirebase(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize FirebaseConfig: ${e.message}", e)
        }

        try {
            AiStudyGuardManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize AiStudyGuardManager: ${e.message}", e)
        }

        try {
            AppDailyLimitsManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize AppDailyLimitsManager: ${e.message}", e)
        }

        try {
            com.example.util.StrictModeManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize StrictModeManager: ${e.message}", e)
        }

        try {
            com.example.util.ShortsLockManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize ShortsLockManager: ${e.message}", e)
        }

        try {
            com.example.util.YouTubeStudyGuardManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize YouTubeStudyGuardManager: ${e.message}", e)
        }

        try {
            com.example.util.DeepFocusManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize DeepFocusManager: ${e.message}", e)
        }

        try {
            ScheduledBlockScheduler.evaluateAndReschedule(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize ScheduledBlockScheduler: ${e.message}", e)
        }
    }
}

