package com.example.gyro_pointer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageButton
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.*

val mainHandler = Handler(Looper.getMainLooper())


private lateinit var mLayout: FrameLayout
private lateinit var windowManager: WindowManager
private lateinit var layoutParams: WindowManager.LayoutParams

private var inputData: String? = null

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private var x: Float = 0F
private var y: Float = 0F

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

        initPointer()

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
        btn.setOnClickListener {
            Log.i("MainService", " kliko san botun")
//            val res: Boolean = dispatchGesture(createClick(5f,5f), callback, null) //bitno
//            Log.i("MainService", "result:   " + res.toString())

//            movePointer()

        }

        startServer()

        mainHandler.post(object : Runnable {
            override fun run() {
                Log.i("MainService", "uso u run")
                Thread.sleep(1000)
                movePointer(x,y)
                mainHandler.postDelayed(this, 1000)
            }
        })


        //windowManager.removeView(mLayout)
    }


    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        Log.i("MainService", "onAccessibilityEvent() called")
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        var data = ""
        if (intent?.getExtras()?.containsKey("data")!!)
            data = intent.getStringExtra("data")
        Log.i("MainService", data)
        return START_STICKY
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

    private fun initPointer() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayout = FrameLayout(this)
        layoutParams = WindowManager.LayoutParams()
        layoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.START
        layoutParams.x = (screenWidth / 2)
        layoutParams.y = 0

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action, mLayout)
        windowManager.addView(mLayout, layoutParams)


    }

    private fun movePointerRand() {

        val x = Random().nextInt(screenWidth)
        val y = Random().nextInt(screenHeight) - screenHeight / 2

        layoutParams.x = x
        layoutParams.y = y

        windowManager.updateViewLayout(mLayout, layoutParams)

    }

    private fun movePointer(x: Float, y: Float) {
        Log.i("MainService", x.toString() + " " + y.toString())
        val x = Random().nextInt(screenWidth)
        val y = Random().nextInt(screenHeight) - screenHeight / 2

        layoutParams.x = x
        layoutParams.y = y

        windowManager.updateViewLayout(mLayout, layoutParams)

    }

    private fun startServer(){
        val thread = Thread(object : Runnable{
            override fun run() {
                try{
                    Log.i("MainService", "uso u try")
                    val sSocket = ServerSocket(9001)
                    val s = sSocket.accept()

                    var input: BufferedReader

                    while (true){
                        Log.i("MainService", "uso u while")
                        input = BufferedReader(InputStreamReader(s.getInputStream()))
                        inputData = input.readLine()
                        Log.i("MainService", inputData)
                        val command = inputData!!.split(" ")
                        x = command[0].toFloat()
                        y = command[1].toFloat()
                        //movePointer()
                    }

                    //                    s.close()
                    //                    sSocket.close()

                }catch (e: IOException){
                    Log.i("MainService", "uso u catch " + e.toString())
                    e.printStackTrace()
                }
            }
        })
        thread.start()
    }


}

