package ru.shlyahten.cvt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shlyahten.cvt.config.VehicleConfigs
import ru.shlyahten.cvt.data.AppSettings
import ru.shlyahten.cvt.data.repository.ObdRepository
import ru.shlyahten.cvt.data.repository.ObdRepositoryImpl
import ru.shlyahten.cvt.domain.usecase.ReadCvtTemperature
import ru.shlyahten.cvt.domain.usecase.ReadOilDegradation
import ru.shlyahten.cvt.obd.CVT_2103_TEMP_COUNT_BYTE_INDEX
import ru.shlyahten.cvt.obd.CvtTempParser
import ru.shlyahten.cvt.ui.CvtTempFormula
import kotlin.math.abs

class CvtOverlayService : Service() {

    companion object {
        private const val TAG = "CvtOverlayService"
        private const val CHANNEL_ID = "cvt_overlay_monitor"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ru.shlyahten.cvt.action.START"
        const val ACTION_STOP = "ru.shlyahten.cvt.action.STOP"
        const val ACTION_RECONNECT = "ru.shlyahten.cvt.action.RECONNECT"
        const val ACTION_READ_OIL = "ru.shlyahten.cvt.action.READ_OIL"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow = _isRunningFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, CvtOverlayService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CvtOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, CvtOverlayService::class.java).apply {
                action = ACTION_RECONNECT
            }
            context.startService(intent)
        }

        fun readOilDegradation(context: Context) {
            val intent = Intent(context, CvtOverlayService::class.java).apply {
                action = ACTION_READ_OIL
            }
            context.startService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var app: CvtApp
    private lateinit var settings: AppSettings

    private var obdRepository: ObdRepository? = null
    private var pollJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tempTextView: TextView? = null
    private var statusDotView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as CvtApp
        settings = AppSettings.getInstance(this)
        _isRunningFlow.value = true

        createNotificationChannel()
        startForegroundNotification(getString(R.string.status_connecting))

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        obdRepository = ObdRepositoryImpl(adapter)

        setupOverlayIfEnabled()
        observeAppStateForOverlay()
        startBackgroundMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                startBackgroundMonitoring()
            }
            ACTION_READ_OIL -> {
                readOilDegradationAsync()
            }
        }
        return START_STICKY
    }

    private fun startBackgroundMonitoring() {
        pollJob?.cancel()
        pollJob = serviceScope.launch(Dispatchers.IO) {
            val repo = obdRepository ?: return@launch
            var consecutiveErrors = 0

            while (isActive) {
                val targetAddress = settings.getSelectedDeviceAddress()
                if (targetAddress.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        app.updateData(null, null, false, "No device selected")
                        updateNotificationText("No paired device selected")
                    }
                    delay(3000)
                    continue
                }

                if (!repo.isConnected()) {
                    withContext(Dispatchers.Main) {
                        app.updateData(null, null, false, getString(R.string.status_connecting))
                        updateNotificationText(getString(R.string.status_connecting))
                    }

                    Log.d(TAG, "Connecting to OBD adapter at $targetAddress...")
                    val connectResult = repo.connect(targetAddress)
                    if (connectResult.isFailure) {
                        val errMsg = connectResult.exceptionOrNull()?.message ?: "Connect failed"
                        Log.w(TAG, "Connect failed: $errMsg. Retrying in 5s...")
                        withContext(Dispatchers.Main) {
                            app.updateData(null, null, false, "Connect error: $errMsg")
                            updateNotificationText("Connect error: $errMsg")
                        }
                        delay(5000)
                        continue
                    }
                    Log.i(TAG, "Connected to OBD adapter!")
                    consecutiveErrors = 0
                }

                // Connected loop: query CVT temperature PID 2103
                try {
                    val readTempUseCase = ReadCvtTemperature(repo)
                    val rawResult = readTempUseCase.execute(ReadCvtTemperature.Formula.RawCount)
                    val n = rawResult.getOrThrow().toInt().coerceIn(0, 255)

                    // Compute values via fast-path Horner's scheme
                    val temp1 = CvtTempParser.convertCountToTemp1(n)
                    val temp2 = CvtTempParser.convertCountToTemp2(n)

                    val activeFormula = settings.getFormula()
                    val displayTemp = when (activeFormula) {
                        CvtTempFormula.Temp1 -> temp1
                        CvtTempFormula.Temp2 -> temp2
                        CvtTempFormula.RawCount -> n.toDouble()
                    }

                    consecutiveErrors = 0

                    withContext(Dispatchers.Main) {
                        app.updateData(displayTemp, n, true, "OK")
                        val unit = if (activeFormula == CvtTempFormula.RawCount) "cnt" else "°C"
                        val notifText = String.format("CVT: %.1f%s (count %d)", displayTemp, unit, n)
                        updateNotificationText(notifText)
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.w(TAG, "OBD query error ($consecutiveErrors): ${e.message}")
                    withContext(Dispatchers.Main) {
                        app.updateData(null, null, true, "Read error: ${e.message}")
                    }

                    if (consecutiveErrors >= 3) {
                        Log.w(TAG, "Too many errors, resetting connection...")
                        repo.disconnect()
                        withContext(Dispatchers.Main) {
                            app.updateData(null, null, false, "Connection lost")
                            updateNotificationText("Connection lost, reconnecting...")
                        }
                        delay(2000)
                    }
                }

                val interval = settings.getPollIntervalMs().coerceAtLeast(300L)
                delay(interval)
            }
        }
    }

    private fun readOilDegradationAsync() {
        serviceScope.launch(Dispatchers.IO) {
            val repo = obdRepository
            if (repo == null || !repo.isConnected()) {
                Log.w(TAG, "Cannot read oil degradation: not connected")
                return@launch
            }
            try {
                val useCase = ReadOilDegradation(repo)
                val degradation = useCase.execute(ReadOilDegradation.Formula.Default).getOrThrow()
                withContext(Dispatchers.Main) {
                    app.updateOilDegradation(degradation)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read oil degradation: ${t.message}", t)
            }
        }
    }

    private fun setupOverlayIfEnabled() {
        if (!settings.isOverlayEnabled() || !Settings.canDrawOverlays(this)) {
            removeOverlayView()
            return
        }
        if (overlayView != null) return

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_cvt_temp1, null)
            overlayView = view
            tempTextView = view.findViewById(R.id.overlay_cvt_temp1_text)
            statusDotView = view.findViewById(R.id.overlay_status_dot)

            val initialX = settings.getOverlayX()
            val initialY = settings.getOverlayY()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
            overlayParams = params

            attachDragAndClickListener(view, params)
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
        }
    }

    private fun attachDragAndClickListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = 12f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(event.rawX - initialTouchX) > touchSlop || abs(event.rawY - initialTouchY) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        settings.setOverlayPosition(params.x, params.y)
                    } else {
                        // Click action: open MainActivity
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun observeAppStateForOverlay() {
        serviceScope.launch {
            settings.overlayFlow.collectLatest { enabled ->
                if (enabled) {
                    setupOverlayIfEnabled()
                } else {
                    removeOverlayView()
                }
            }
        }

        serviceScope.launch {
            app.cvtTemp1C.collectLatest { temp ->
                val tv = tempTextView ?: return@collectLatest
                val dot = statusDotView
                val formula = settings.getFormula()

                if (temp != null) {
                    val displayStr = if (formula == CvtTempFormula.RawCount) {
                        "${temp.toInt()} cnt"
                    } else {
                        String.format("%.1f°C", temp)
                    }
                    tv.text = displayStr

                    // Dynamic color coding for temperature zones
                    val (textColor, dotColor) = when {
                        formula == CvtTempFormula.RawCount -> Color.WHITE to Color.parseColor("#38BDF8")
                        temp < 50.0 -> Color.parseColor("#38BDF8") to Color.parseColor("#38BDF8") // Cold (Ice Blue)
                        temp in 50.0..89.9 -> Color.parseColor("#22C55E") to Color.parseColor("#22C55E") // Optimal (Emerald Green)
                        temp in 90.0..99.9 -> Color.parseColor("#F59E0B") to Color.parseColor("#F59E0B") // Warm / Elevated (Amber)
                        else -> Color.parseColor("#EF4444") to Color.parseColor("#EF4444") // Hot / Overheat (Crimson)
                    }

                    tv.setTextColor(textColor)
                    dot?.background?.let { d ->
                        if (d is GradientDrawable) {
                            d.setColor(dotColor)
                        }
                    }
                } else {
                    tv.text = getString(R.string.overlay_cvt_temp1_no_data)
                    tv.setTextColor(Color.parseColor("#94A3B8"))
                    dot?.background?.let { d ->
                        if (d is GradientDrawable) {
                            d.setColor(Color.parseColor("#64748B"))
                        }
                    }
                }
            }
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
        tempTextView = null
        statusDotView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    private fun startForegroundNotification(initialText: String) {
        val notification = buildForegroundNotification(initialText)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationText(text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val notification = buildForegroundNotification(text)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, CvtOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.screen_main_button_disconnect), stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        _isRunningFlow.value = false
        pollJob?.cancel()
        serviceScope.cancel()
        removeOverlayView()
        obdRepository?.close()
        obdRepository = null
        app.updateData(null, null, false, "Stopped")
        super.onDestroy()
    }
}

