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
        const val ACTION_TS_POWER_ON = "com.ts.headunit.power.on"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        val settings = AppSettings.getInstance(context)

        // 1. Handle ACC OFF / Shutdown:
        if (action == ACTION_GLSX_ACCOFF || action == ACTION_FYT_ACCOFF || action == Intent.ACTION_SHUTDOWN) {
            if (settings.isKeepAliveEnabled()) {
                Log.i(TAG, "ACC OFF / shutdown received ($action). Keep-Alive enabled -> switching service to standby")
                runCatching {
                    CvtOverlayService.standby(context)
                }.onFailure { t ->
                    Log.e(TAG, "Failed to put service to standby on ACCOFF", t)
                }
            } else {
                Log.i(TAG, "ACC OFF / shutdown received ($action). Stopping CvtOverlayService...")
                runCatching {
                    CvtOverlayService.stop(context)
                }.onFailure { t ->
                    Log.e(TAG, "Failed to stop service on ACCOFF", t)
                }
            }
            return
        }

        // 2. Filter BT state changes if BT turned off
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state != BluetoothAdapter.STATE_ON) {
                return
            }
        }

        val isAutostartAllowed = settings.isAutostartEnabled() || settings.isAutoconnectEnabled() || settings.isKeepAliveEnabled()
        if (!isAutostartAllowed) {
            Log.d(TAG, "Autostart / Keep-Alive is disabled in settings. Skipping.")
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

        Log.i(TAG, "Starting CvtOverlayService on boot/wake/ACC/BT (action: $action, device: ${deviceAddress ?: "auto"})")
        runCatching {
            CvtOverlayService.start(context)
        }.onFailure { t ->
            Log.e(TAG, "Failed to start service on boot/wake/ACC/BT", t)
        }
    }
}
