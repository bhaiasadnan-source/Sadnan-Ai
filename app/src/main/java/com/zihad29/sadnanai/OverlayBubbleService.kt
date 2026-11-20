package com.zihad29.sadnanai

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Floating overlay bubble that appears on top of other apps.
 * Draggable, tap opens MainActivity.
 */
class OverlayBubbleService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var bubble: FrameLayout
    private lateinit var lp: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(56)

        bubble = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bubble_bg)
            addView(
                TextView(this@OverlayBubbleService).apply {
                    text = "S"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 22f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
            )
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = lp.x
                        initY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                        lp.x = initX + dx
                        lp.y = initY + dy
                        wm.updateViewLayout(bubble, lp)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val i = Intent(this@OverlayBubbleService, MainActivity::class.java)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(i)
                        }
                    }
                }
                return true
            }
        })

        wm.addView(bubble, lp)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        try { wm.removeView(bubble) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
