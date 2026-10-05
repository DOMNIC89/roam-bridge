package com.roambridge.app

import android.app.Application
import com.roambridge.app.service.HeartbeatWorker

class RoamBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic health & heartbeat worker.
        // The worker is also responsible for starting BridgeForegroundService when needed,
        // because startForegroundService() is not allowed from a background Application.onCreate()
        // on Android 12+ (ForegroundServiceStartNotAllowedException).
        HeartbeatWorker.schedule(this)
    }
}
