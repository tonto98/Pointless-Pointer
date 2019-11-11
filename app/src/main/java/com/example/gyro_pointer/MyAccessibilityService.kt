package com.example.gyro_pointer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.widget.ImageButton


private lateinit var mLayout: FrameLayout
private lateinit var windowManager: WindowManager
private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

class MyAccessibilityService : AccessibilityService() {

    override fun onInterrupt() {
        Log.i("MainService", "onInterrupt() called")
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.

    }

    override fun onServiceConnected() {
        Log.i("MainService", "onServiceConnected() called")

//        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
//        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        Log.i("MainService", "H: " + screenHeight + " W: " + screenWidth)

//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayout = FrameLayout(this)
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.START
        layoutParams.x = (screenWidth/2)
        layoutParams.y = 0
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action, mLayout)
        windowManager.addView(mLayout, layoutParams)

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                Log.d("MainService", "gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.d("MainService", "gesture cancelled")
            }
        }

        val btn: ImageButton = mLayout.findViewById(R.id.action_button)
        btn.setOnClickListener{
            Log.i("MainService", " kliko san botun")
            val res: Boolean = dispatchGesture(createClick(5f,5f), callback, null)
            Log.i("MainService", "result:   " + res.toString())
        }
        //windowManager.removeView(mLayout)
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        Log.i("MainService", "onAccessibilityEvent() called")
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    private fun createClick(x: Float, y: Float): GestureDescription {
        // for a single tap a duration of 1 ms is enough
        val DURATION = 1

        val clickPath = Path()
        clickPath.moveTo(x, y)
        val clickStroke = GestureDescription.StrokeDescription(clickPath, 0, DURATION.toLong())
        val clickBuilder = GestureDescription.Builder()
        clickBuilder.addStroke(clickStroke)
        return clickBuilder.build()
    }

}
