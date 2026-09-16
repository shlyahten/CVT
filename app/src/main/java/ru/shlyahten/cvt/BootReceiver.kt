package ru.shlyahten.cvt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.shlyahten.cvt.data.AppSettings

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CvtBootReceiver"
        const val ACTION_GLSX_ACCON = "com.glsx.boot.ACCON"
        const val ACTION_FYT_ACCON = "com.fyt.boot.ACCON"
        const val ACTION_GLSX_ACCOFF = "com.glsx.boot.ACCOFF"
        const val ACTION_FYT_ACCOFF = "com.fyt.boot.ACCOFF"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        if (action == ACTION_GLSX_ACCOFF || action == ACTION_FYT_ACCOFF || action == Intent.ACTION_SHUTDOWN) {
            Log.i(TAG, "ACC OFF / shutdown received ($action). Stopping CvtOverlayService...")
            runCatching {
                CvtOverlayService.stop(context)
            }.onFailure { t ->
                Log.e(TAG, "Failed to stop service on ACCOFF", t)
            }
            return
        }

        val settings = AppSettings.getInstance(context)
        if (!settings.isAutostartEnabled()) {
            Log.d(TAG, "Autostart is disabled in settings. Skipping.")
            return
        }

        if (settings.isAutoEnableBluetoothEnabled()) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && !adapter.isEnabled) {
                Log.i(TAG, "Boot/wake/ACC ($action): auto-enabling Bluetooth (Bluetooth 2)...")
                runCatching {
                    @Suppress("DEPRECATION")
                    adapter.enable()
                }
            }
        }

        val deviceAddress = settings.getSelectedDeviceAddress()
        if (deviceAddress.isNullOrBlank()) {
            Log.w(TAG, "Autostart enabled but no paired device saved in settings. Starting service for auto-detection.")
        }

        Log.i(TAG, "Starting CvtOverlayService on boot/wake/ACC (action: $action, device: ${deviceAddress ?: "auto"})")
        runCatching {
            CvtOverlayService.start(context)
        }.onFailure { t ->
            Log.e(TAG, "Failed to start service on boot/wake/ACC", t)
        }
    }
}
