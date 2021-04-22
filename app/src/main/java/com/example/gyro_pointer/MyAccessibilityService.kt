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
import android.view.SurfaceHolder
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MyAccessibilityService : AccessibilityService(),
    SurfaceHolder.Callback {

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private lateinit var mLayoutCursor: FrameLayout
    private lateinit var mLayoutProgress: FrameLayout
    private lateinit var mLayoutToggle: FrameLayout
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParamsCursor: WindowManager.LayoutParams
    private lateinit var layoutParamsProgress: WindowManager.LayoutParams

    private var inputData: String? = null

    private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
    private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

    private var roll: Float = 0F
    private var pitch: Float = 0F

    private var pointer = Pointer()
    private var pointerLast = pointer.copy()

    private var clickTimer = 0

    private var isCursorActivated: Boolean = false

    lateinit var progressBar: ProgressBar
    lateinit var toggleButton: Button

    private val backgroundThreadScheduler = Executors.newScheduledThreadPool(3)

    override fun onInterrupt() {
        Log.i("vito_log", "onInterrupt() called")
    }

    override fun onCreate() {
        Log.i("vito_log", "onCreate() called")
        super.onCreate()
    }

    private fun initShit() {
        Log.i("vito_log", "H: " + screenHeight + " W: " + screenWidth)

        initPointer()
        initProgressBar()
        initToggler()

        progressBar = mLayoutProgress.findViewById(R.id.progressBar)
        toggleButton = mLayoutToggle.findViewById(R.id.switch_cursor_toggle)
        toggleButton.setOnClickListener {
            isCursorActivated = !isCursorActivated
            if(isCursorActivated){
                showCursor()
            }else{
                hideCursor()
            }
        }

        startPointer()
        startServer()

    }

    override fun onServiceConnected() {
        Log.d("vito_log", "onServiceConnected")
        initShit()

        // https://developer.android.com/reference/android/hardware/Camera.html#setPreviewCallback(android.hardware.Camera.PreviewCallback)
        // https://stackoverflow.com/questions/35987346/getting-the-raw-camera-data-on-android
    }


    private fun startPointer() {

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                //Log.d("vito_log", "gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.d("vito_log", "gesture cancelled")
            }
        }

        // todo remove thiss????????????????????
        backgroundThreadScheduler.scheduleAtFixedRate({
            // TODO can this execute on background thread?
            mainThreadHandler.post {
                //Log.i("vito_log", "uso u run")
                if (isCursorActivated) {
                    pointerLast = pointer.copy()
//                    pointer.translateCommands(roll, pitch)
//                    pointer.translateCommandsCamera(roll, pitch)
                    pointer.translateCommandsHelmet(roll, pitch)
                    movePointer(pointer)

                    if ((pointer.x >= pointerLast.x -1 && pointer.x <= pointerLast.x +1) &&
                        (pointer.y >= pointerLast.y -1 && pointer.y <= pointerLast.y +1)) {
                        clickTimer += 2
                        progressBar.progress = clickTimer
                        if (clickTimer == 68) {
                            clickTimer = 0
                            val res: Boolean = dispatchGesture(
                                createClick(
                                    pointer.x.toFloat() - 1,
                                    pointer.y.toFloat() + screenHeight / 2 - 1
                                ), callback, null
                            ) //bitno
                            //Log.i("vito_log", "result:   " + res.toString())
                        }
                    } else {
                        clickTimer = 0
                        progressBar.progress = 0
                    }
                } else {
                    clickTimer = 0
                    progressBar.progress = 0
                }
            }
        }, 0, 30L, TimeUnit.MILLISECONDS)
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        Log.i("MainService", "onAccessibilityEvent() called")
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        var data = ""
        if (intent?.getExtras()?.containsKey("toggle")!!)
            data = intent.getStringExtra("toggle")!!
        Log.i("MainService", data)
        if (data == "on") {
            isCursorActivated = true
            showCursor()
        } else if (data == "off") {
            isCursorActivated = false
            hideCursor()
        }
        return START_STICKY
    }

    private fun createClick(x: Float, y: Float): GestureDescription {
        // for a single tap a duration of 1 ms is enough
        val DURATION = 1
        mainThreadHandler.post{
            Log.d("vito_log", "layout x: ${layoutParamsCursor.x} y: ${layoutParamsCursor.y}")
            Log.d("vito_log", "kursor x: ${x} y: ${y}")
        }
        val clickPath = Path()
        clickPath.moveTo(x, y+60) // +110 na y mi je kinda popravilo idk vidit cemo
        val clickStroke = GestureDescription.StrokeDescription(clickPath, 0, DURATION.toLong())
        val clickBuilder = GestureDescription.Builder()
        clickBuilder.addStroke(clickStroke)
        return clickBuilder.build()
    }

    private fun initPointer() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutCursor = FrameLayout(this)
        layoutParamsCursor = WindowManager.LayoutParams()
        layoutParamsCursor.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsCursor.format = PixelFormat.TRANSLUCENT
        layoutParamsCursor.flags =
            layoutParamsCursor.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsCursor.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCursor.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCursor.gravity = Gravity.START
        layoutParamsCursor.x = (screenWidth / 2)
        layoutParamsCursor.y = 0
        mLayoutCursor.visibility = INVISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.cursor, mLayoutCursor)
        windowManager.addView(mLayoutCursor, layoutParamsCursor)
    }

    private fun initProgressBar() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutProgress = FrameLayout(this)
        layoutParamsProgress = WindowManager.LayoutParams()
        layoutParamsProgress.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsProgress.format = PixelFormat.TRANSLUCENT
        layoutParamsProgress.flags =
            layoutParamsProgress.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsProgress.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.gravity = Gravity.START
        layoutParamsProgress.x = 0
        layoutParamsProgress.y = screenHeight / 2
        mLayoutProgress.visibility = INVISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.progress_bar, mLayoutProgress)
        windowManager.addView(mLayoutProgress, layoutParamsProgress)
    }

    private fun initToggler() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutToggle = FrameLayout(this)
        layoutParamsCursor = WindowManager.LayoutParams()
        layoutParamsCursor.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsCursor.format = PixelFormat.TRANSLUCENT
        layoutParamsCursor.flags =
            layoutParamsCursor.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsCursor.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCursor.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCursor.gravity = Gravity.START
        layoutParamsCursor.x = screenWidth
        layoutParamsCursor.y = -1*screenHeight/2
        mLayoutToggle.visibility = VISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.toggle, mLayoutToggle)
        windowManager.addView(mLayoutToggle, layoutParamsCursor)
    }


    private fun movePointer(pointer: Pointer) {

        layoutParamsCursor.x = pointer.x
        layoutParamsCursor.y = pointer.y
//        Log.i("MainService", pointer.x.toString() + " " + pointer.y.toString())

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

    }


    private fun startServer() {
        val thread = Thread(Runnable {
            try {
                Log.i("MainService", "uso u try")
                val sSocket = ServerSocket(9001)
                val s = sSocket.accept()

                var input: BufferedReader

                while (true) {
                    Log.i("MainService", "uso u while")
                    input = BufferedReader(InputStreamReader(s.getInputStream()))
                    inputData = input.readLine()
                    if(inputData == null){
                        Log.d("MainService", "input data is null, stopping?")
                        s.close()
                        sSocket.close()
                        break
                    }
                    Log.i("MainService", inputData)
                    val command = inputData!!.split(" ")
                    pitch = command[0].toFloat() //mozda obrnuto !!
                    roll = command[1].toFloat() //mozda obrnuto !!
                    //movePointer()
                    Thread.sleep(10)
                }

                //                    s.close()
                //                    sSocket.close()

            } catch (e: IOException) {
                Log.i("MainServiceERROR", "uso u catch " + e.toString())
                e.printStackTrace()
            }
        })
        thread.start()
    }

    private fun showCursor() {
        mLayoutCursor.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

    private fun hideCursor() {
        mLayoutCursor.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Log.d("vito_log", "called surfaceChanged")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d("vito_log", "called surfaceDestroyed")
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d("vito_log", "called surfaceCreated")
    }

}

