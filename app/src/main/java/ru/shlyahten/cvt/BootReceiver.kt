package ru.shlyahten.cvt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.shlyahten.cvt.data.AppSettings

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CvtBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        val settings = AppSettings.getInstance(context)
        if (!settings.isAutostartEnabled()) {
            Log.d(TAG, "Autostart is disabled in settings. Skipping.")
            return
        }

        val deviceAddress = settings.getSelectedDeviceAddress()
        if (deviceAddress.isNullOrBlank()) {
            Log.w(TAG, "Autostart enabled but no paired device selected. Skipping.")
            return
        }

        Log.i(TAG, "Starting CvtOverlayService on boot/wake for device $deviceAddress")
        runCatching {
            CvtOverlayService.start(context)
        }.onFailure { t ->
            Log.e(TAG, "Failed to start service on boot", t)
        }
    }
}
