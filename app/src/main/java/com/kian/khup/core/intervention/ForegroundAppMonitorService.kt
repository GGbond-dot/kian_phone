package com.kian.khup.core.intervention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.media.AudioManager
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.kian.khup.MainActivity
import com.kian.khup.R
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.data.repository.InterventionRepository
import com.kian.khup.core.data.repository.InterventionRuleStatus
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForegroundAppMonitorService : Service() {

    @Inject lateinit var usageStatsCollector: UsageStatsCollector
    @Inject lateinit var usageStatsRepository: UsageStatsRepository
    @Inject lateinit var interventionRepository: InterventionRepository

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val cooldownUntilByRule = mutableMapOf<String, Long>()
    private var monitorJob: Job? = null
    private var overlayView: View? = null
    private var activeRuleId: String? = null
    private var volumeBeforeGate: Int? = null

    override fun onCreate() {
        super.onCreate()
        val foregroundStarted = runCatching {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }.onFailure {
            Log.w(TAG, "failed to start foreground monitor service", it)
            stopSelf()
        }.isSuccess
        if (!foregroundStarted) return

        monitorJob = scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        monitorJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            runCatching { checkForegroundApp() }
                .onFailure { Log.w(TAG, "foreground app check failed", it) }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun checkForegroundApp() {
        if (!NotificationPermissions.hasUsageAccess(this) || !Settings.canDrawOverlays(this)) return
        if (overlayView != null) return

        val packageName = usageStatsCollector.getCurrentForegroundPackage() ?: return
        if (packageName == applicationContext.packageName) return
        if (!interventionRepository.isMonitoredPackage(packageName)) return

        usageStatsRepository.syncToday()
        val rule = interventionRepository.getExceededRuleForPackage(packageName) ?: return
        val now = System.currentTimeMillis()
        val cooldownUntil = cooldownUntilByRule[rule.ruleId] ?: 0L
        if (now < cooldownUntil) return

        showPurposeOverlay(rule)
    }

    private fun showPurposeOverlay(rule: InterventionRuleStatus) {
        activeRuleId = rule.ruleId
        lowerMediaVolume()
        val input = EditText(this).apply {
            hint = "例如：回复朋友消息 / 查一个教程 / 发完就走"
            minLines = 2
            maxLines = 4
            setTextColor(Color.rgb(24, 24, 27))
            setHintTextColor(Color.rgb(113, 113, 122))
            textSize = 16f
            setSingleLine(false)
            setPadding(28, 22, 28, 22)
            background = roundedRect(Color.WHITE, 18f)
        }
        val confirmButton = Button(this).apply {
            text = "写好了，继续"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = roundedRect(Color.rgb(37, 99, 235), 18f)
            isEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(
                TextView(this@ForegroundAppMonitorService).apply {
                    text = "${rule.appLabel} 已超过今日阈值"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                TextView(this@ForegroundAppMonitorService).apply {
                    text = "先写下这次打开它的目的。写完后才继续。"
                    textSize = 15f
                    setTextColor(Color.rgb(226, 232, 240))
                    gravity = Gravity.CENTER
                    setPadding(0, 16, 0, 32)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                confirmButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 24 },
            )
        }
        val closeButton = TextView(this).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "退出 ${rule.appLabel}"
            setOnClickListener {
                restoreMediaVolume()
                launchHome()
                removeOverlay()
            }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(232, 15, 23, 42))
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            addView(
                closeButton,
                FrameLayout.LayoutParams(
                    dp(56),
                    dp(56),
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = dp(28)
                    marginEnd = dp(24)
                },
            )
        }

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val enabled = !s.isNullOrBlank()
                    confirmButton.isEnabled = enabled
                    confirmButton.background = roundedRect(
                        if (enabled) Color.rgb(37, 99, 235) else Color.rgb(100, 116, 139),
                        18f,
                    )
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        confirmButton.setOnClickListener {
            val purpose = input.text.toString().trim()
            if (purpose.isBlank()) return@setOnClickListener
            cooldownUntilByRule[rule.ruleId] = System.currentTimeMillis() + PURPOSE_COOLDOWN_MS
            scope.launch {
                interventionRepository.recordPurposeGate(rule, purpose)
                restoreMediaVolume()
                removeOverlay()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        val wm = getSystemService(WindowManager::class.java)
        wm.addView(root, params)
        overlayView = root
        input.requestFocus()
        getSystemService(InputMethodManager::class.java)
            .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching {
            getSystemService(WindowManager::class.java).removeView(view)
        }
        overlayView = null
        activeRuleId = null
    }

    private fun launchHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        }.onFailure {
            Log.w(TAG, "failed to launch home from purpose gate", it)
        }
    }

    private fun lowerMediaVolume() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (volumeBeforeGate == null) {
                volumeBeforeGate = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                0,
            )
        }.onFailure {
            Log.w(TAG, "failed to lower media volume", it)
        }
    }

    private fun restoreMediaVolume() {
        val previousVolume = volumeBeforeGate ?: return
        volumeBeforeGate = null
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
        }.onFailure {
            Log.w(TAG, "failed to restore media volume", it)
        }
    }

    private fun buildForegroundNotification(): android.app.Notification {
        ensureChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KHUP 正在守住算法 App")
            .setContentText("超过阈值后，再打开抖音/小红书会先询问目的。")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "前台干预监控",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun roundedRect(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "KHUP/ForegroundMonitor"
        private const val CHANNEL_ID = "khup_foreground_monitor"
        private const val NOTIFICATION_ID = 4201
        private const val POLL_INTERVAL_MS = 3_000L
        private val PURPOSE_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(15)

        fun start(context: Context) {
            val intent = Intent(context, ForegroundAppMonitorService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
