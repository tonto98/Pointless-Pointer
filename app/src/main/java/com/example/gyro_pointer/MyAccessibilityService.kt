package com.example.gyro_pointer

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.graphics.BitmapFactory.decodeByteArray
import android.hardware.*
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.sql.Time
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

typealias faceOrientationListener = (faceOrientation: FaceOrientation) -> Unit

class MyAccessibilityService : AccessibilityService(), SensorEventListener, Camera.PictureCallback,
    SurfaceHolder.Callback {

    companion object {
        private const val TAG = "CameraXBasic"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private var mServiceCamera: Camera? = null
    }

    // todo use mainLooper
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val backgroundThreadHandler = Executors.newSingleThreadExecutor()


    // this is an example
    init {
        mainThreadHandler.post {
            // ovo na main threadu
        }

        backgroundThreadHandler.submit {
            // ovo se na main threadu izvrsava
        }
    }

    fun runOnMainThread(runnable: Runnable) {
        mainThreadHandler.post(runnable)
    }

    fun runOnBackgroundThread(runnable: Runnable){
        backgroundThreadHandler.submit(runnable)
    }

    private lateinit var mLayoutCursor: FrameLayout
    private lateinit var mLayoutProgress: FrameLayout
    private lateinit var mLayoutCameraView: FrameLayout
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParamsCursor: WindowManager.LayoutParams
    private lateinit var layoutParamsProgress: WindowManager.LayoutParams
    private lateinit var layoutParamsCameraView: WindowManager.LayoutParams

    private var inputData: String? = null

    private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
    private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

    private var roll: Float = 0F
    private var pitch: Float = 0F

    private var pointer = Pointer()
    private var pointerLast = pointer.copy()

    private var clickTimer = 0

    private var isCursorActivated: Boolean = false

    private lateinit var sensorManager: SensorManager
    private var mLight: Sensor? = null

    private var imageCapture: ImageCapture? = null

    lateinit var progressBar: ProgressBar
    private lateinit var detector: FaceDetector
    private var safeToCapture: Boolean = true

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val backgroundThreadScheduler = Executors.newScheduledThreadPool(3)

    private var mCamera: Camera? = null

    private var mSurfaceView: SurfaceView? = null
    private var mSurfaceHolder: SurfaceHolder? = null

    //imageview koj je sluzio za skuzit koj kurac krasni se desava sa rotacijom
    //private var cameraImageView: ImageView? = null

    override fun onInterrupt() {
        Log.i("vito_log", "onInterrupt() called")
    }

    override fun onCreate() {
        Log.i("vito_log", "onCreate() called")
        super.onCreate()
    }

    private fun initShit() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mLight?.also { light ->
            Log.i("vito_log", "register listener")
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }

        Log.i("vito_log", "H: " + screenHeight + " W: " + screenWidth)

        initPointer()
        initProgressBar()
        initCameraView()

        progressBar = mLayoutProgress.findViewById(R.id.progressBar)

        startPointer()

        detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())
    }

    override fun onServiceConnected() {
        Log.d("vito_log", "onServiceConnected")
        initShit()

        // TODO mCamera is old camera, mServiceCamera is new camera
//        openMCamera()
//        openMServiceCamera()

//        backgroundThreadScheduler.scheduleAtFixedRate({
//            // todo uncomment me to take pictures
//            takePicture()
//        }, 0, 1000, TimeUnit.MILLISECONDS)


        // https://developer.android.com/reference/android/hardware/Camera.html#setPreviewCallback(android.hardware.Camera.PreviewCallback)
        // https://stackoverflow.com/questions/35987346/getting-the-raw-camera-data-on-android
    }

    var frameCount = 0 // maybe smort
    var readyToProcessNextFrame = true
    private fun openMServiceCamera() {

        val params = mServiceCamera?.parameters
        mServiceCamera?.parameters = params

        val p = mServiceCamera?.parameters
        val listSize = p?.supportedPreviewSizes
        val mPreviewSize = listSize!![15]

        listSize!!.forEach { size ->
            Log.d("vito_log", "preview-> width: " + size.width.toString() + " height: " + size.height.toString())
        }

        p.setPreviewSize(mPreviewSize.width, mPreviewSize.height)
        p.previewFormat = PixelFormat.YCbCr_420_SP // todo try ImageFormat.NV21
        mServiceCamera?.parameters = p

        try {
            mServiceCamera?.setDisplayOrientation(90)
            mServiceCamera?.setPreviewDisplay(mSurfaceHolder)
            mServiceCamera?.startPreview()

            Log.d("vito_log", "started preview very good")

            mServiceCamera?.setPreviewCallback { data, camera ->
                if(readyToProcessNextFrame){    //if (frameCount >= 10){ // the IF
                    //Log.d("vito_log", "got camera data, size: ${data.size}")
                    runOnBackgroundThread(
                        Runnable{
                            readyToProcessNextFrame = false
                            frameCount = 0

                            if(data == null || data.isEmpty()) {
                                throw java.lang.RuntimeException()
                            }

                            val aprams = camera.parameters
                            val iwdth = aprams.previewSize.width
                            val ehight = aprams.previewSize.height

                            val img = InputImage.fromByteArray(
                                data,
                                iwdth,
                                ehight,
                                270,
                                InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
                            )

/*
                            val yuvImg = YuvImage(data, aprams.previewFormat, iwdth, ehight, null)
                            val out = ByteArrayOutputStream()
                            yuvImg.compressToJpeg(Rect(0, 0, iwdth, ehight), 50, out)

                            val bytes = out.toByteArray()

                            val img = decodeByteArray(bytes, 0, bytes.size).rotate(270F)
*/
                            //imageview koj je sluzio za skuzit koj kurac krasni se desava sa rotacijom
                            // cameraImageView?.setImageBitmap(img)


                            if(img != null) {

                                //val image = InputImage.fromBitmap(img, 0)
                                //Log.i("FACE", image.height.toString() + image.width.toString())
                                //Log.i("FACE", image.toString())
                                var res = detector.process(img) // was image !!! todo
                                    .addOnSuccessListener { faces ->

                                        Log.d("FACE", faces.size.toString())
                                        if(faces.size > 0){
//                    listener(FaceOrientation(faces[0].headEulerAngleY, faces[0].headEulerAngleZ))

                                            // https://developers.google.com/ml-kit/vision/face-detection/android

                                            Log.d("vito_log", "more than 0 faces-> X: ${faces[0].headEulerAngleX}, Y: ${faces[0].headEulerAngleY}, Z: ${faces[0].headEulerAngleZ}")
                                            pitch = -1*faces[0].headEulerAngleX
                                            roll = faces[0].headEulerAngleZ
                                            readyToProcessNextFrame = true
                                        } else {
                                            Log.d("vito_log", "<= 0 faces")
//
                                            readyToProcessNextFrame = true
                                        }
//                        faces[0].headEulerAngleY

                                    }
                                    .addOnFailureListener { e ->
                                        Log.d("FACE", "error boy: $e")
                                    }
                            } else {
                                Log.d("vito_log", "cant decode image")
                            }
                        }
                    )


                } // the if for the frame count

            }
        } catch (throwable: Throwable) {
            Log.d("vito_log", "error starting preview, setting preview callback")
        }

        // TODO try this, use reconnect to reclaim camera, check docs
//        mServiceCamera?.unlock()
    }

    fun Bitmap.rotate(degrees: Float): Bitmap{
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(this, 0,0,width,height,matrix,true)
    }


    // unused
    private fun takePicture() {
        try {
//            val sv = SurfaceView(applicationContext)

            // todo 2 lines comment
//            val surfaceTexture = SurfaceTexture(10)
//            mCamera?.setPreviewTexture(surfaceTexture)


            //mCamera.setPreviewDisplay(sv.getHolder());
//            val parameters = mCamera?.parameters
//
//            //set camera parameters
//            mCamera?.parameters = parameters

            //This boolean is used as app crashes while writing images to file if simultaneous calls are made to takePicture
            if (safeToCapture) {
                mCamera?.startPreview()
                mCamera?.takePicture(null, null, this)
            }
            //Get a surface
//            val sHolder = sv.holder
//            //tells Android that this surface will have its data constantly replaced
//            sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        } catch (e: Throwable) {
            mainThreadHandler.post {
                Log.d("vito_log", "error on camera and surface view thingy: $e")
            }
        }
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
//                Log.i("vito_log", "uso u run")
                if (isCursorActivated) {
                    pointerLast = pointer.copy()
//                    pointer.translateCommands(roll, pitch)
                    pointer.translateCommandsCamera(roll, pitch)
                    movePointer(pointer)

                    if (pointer.x == pointerLast.x && pointer.y == pointerLast.y) {
                        clickTimer += 2
                        progressBar.progress = clickTimer
                        if (clickTimer == 90) {
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

    // unused
    private fun openMCamera() {
        mCamera = getAvailableFrontCamera() // globally declared instance of camera
        if (mCamera == null) {
            try {
                mCamera = Camera.open() //Take rear facing camera only if no front camera available
            } catch (e: Throwable) {
                Log.d("vito_log", "camera open error: $e")
            }
        }

//        val sv = SurfaceView(applicationContext)
//        val surfaceHolder = sv.holder
//        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
//        surfaceHolder.addCallback(this)
//        val surfaceTexture = SurfaceTexture(10)
//        mCamera?.setPreviewTexture(surfaceTexture)
//        mCamera?.setPreviewDisplay(surfaceHolder)

        val surfaceTexture = SurfaceTexture(10)
        mCamera?.setPreviewTexture(surfaceTexture)

        mCamera?.startPreview()

        mCamera?.setPreviewCallback { data, camera ->
            Log.d("vito_log", "got camera data, size: ${data.size}")

            // https://stackoverflow.com/questions/16334936/onpreviewframe-not-called-after-some-time-before-it-worked-great
            // https://stackoverflow.com/questions/14412618/android-onpreviewframe-is-not-called
        }
    }


    // unused
    override fun onPictureTaken(data: ByteArray?, camera: Camera?) {
        safeToCapture = false

        Log.d("vito_log", "got new camera frame captured")
        // TODO analyze

//        val img: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        img.copyPixelsFromBuffer(ByteBuffer.wrap(data!!))
//
//        val image = InputImage.fromBitmap(img, 0)
//        val result = detector.process(image)
//            .addOnSuccessListener { faces ->
//                Log.d("FACE", faces.size.toString())
//                if(faces.size > 0){
////                    listener(FaceOrientation(faces[0].headEulerAngleY, faces[0].headEulerAngleZ))
//                    Log.d("vito_log", "more than 0 faces")
//                } else {
//                    Log.d("vito_log", "<= 0 faces")
//                }
////                        faces[0].headEulerAngleY
//
//            }
//            .addOnFailureListener { e ->
//                // Task failed with an exception
//                // ...
//                Log.d("FACE", "error boy: $e")
//            }
//
        safeToCapture = true //Set a boolean to true again after saving file.
    }


    private fun getAvailableFrontCamera(): Camera? {
        var cameraCount = 0
        var cam: Camera? = null
        val cameraInfo: Camera.CameraInfo = Camera.CameraInfo()
        cameraCount = Camera.getNumberOfCameras()
        for (camIdx in 0 until cameraCount) {
            Camera.getCameraInfo(camIdx, cameraInfo)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx)
                } catch (e: RuntimeException) {
                    Log.e(
                        "CAMERA",
                        "Camera failed to open: " + e.localizedMessage
                    )
                }
            }
        }
        return cam
    }

    // unused
    private class YourImageAnalyzer(private val listener: faceOrientationListener) :
        ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                // ...
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())
                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        // Task completed successfully
                        // ...
                        Log.d("FACE", faces.size.toString())
                        if (faces.size > 0) {
                            listener(
                                FaceOrientation(
                                    faces[0].headEulerAngleY,
                                    faces[0].headEulerAngleZ
                                )
                            )
                        }
//                        faces[0].headEulerAngleY

                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                        Log.d("FACE", "error boy: $e")
                    }
                    .addOnCompleteListener {
                        mediaImage.close()
                        imageProxy.close()
                    }


            }
        }
    }
    // unused
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
/*
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }
*/
            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer { faceOrientation ->
                        Log.d(
                            "AAAAAAA",
                            "penis1: ${faceOrientation.orientationY}  penis2: ${faceOrientation.orientationZ}"
                        )
                    })
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(this,cameraSelector,)
/*
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
*/
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
        Log.i("MainServiceSensor", "accuracy changed to: " + accuracy.toString())
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // Do something with this sensor value.
        // todo uncomment if you want to see x y z values
//        Log.i("MainServiceSensor", x.roundToInt().toString() + " " + y.roundToInt().toString() + " " + z.roundToInt().toString())
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

        val clickPath = Path()
        clickPath.moveTo(x, y)
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

    private fun initCameraView() {

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutCameraView = FrameLayout(this)
        layoutParamsCameraView = WindowManager.LayoutParams()
        layoutParamsCameraView.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsCameraView.format = PixelFormat.TRANSLUCENT
        layoutParamsCameraView.flags =
            layoutParamsCameraView.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsCameraView.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCameraView.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsCameraView.gravity = Gravity.START
        layoutParamsCameraView.x = 0
        layoutParamsCameraView.y = -1*screenHeight / 2
        mLayoutCameraView.visibility = INVISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.camera_view, mLayoutCameraView)
        windowManager.addView(mLayoutCameraView, layoutParamsCameraView)
    }

    private fun movePointer(pointer: Pointer) {

        layoutParamsCursor.x = pointer.x
        layoutParamsCursor.y = pointer.y
//        Log.i("MainService", pointer.x.toString() + " " + pointer.y.toString())

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

    }

    private fun movePointerRandom(x: Float, y: Float) {
        Log.i("MainService", x.toString() + " " + y.toString())
        val x = Random().nextInt(screenWidth)
        val y = Random().nextInt(screenHeight) - screenHeight / 2

        layoutParamsCursor.x = x
        layoutParamsCursor.y = y

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

    }

    private fun startServer() {
        val thread = Thread(object : Runnable {
            override fun run() {
                try {
                    Log.i("MainService", "uso u try")
                    val sSocket = ServerSocket(9001)
                    val s = sSocket.accept()

                    var input: BufferedReader

                    while (true) {
                        Log.i("MainService", "uso u while")
                        input = BufferedReader(InputStreamReader(s.getInputStream()))
                        inputData = input.readLine()
                        Log.i("MainService", inputData)
                        val command = inputData!!.split(" ")
                        pitch = command[0].toFloat() //mozda obrnuto !!
                        roll = command[1].toFloat() //mozda obrnuto !!
                        //movePointer()
                    }

                    //                    s.close()
                    //                    sSocket.close()

                } catch (e: IOException) {
                    Log.i("MainService", "uso u catch " + e.toString())
                    e.printStackTrace()
                }
            }
        })
        thread.start()
    }

    private fun showCursor() {
        mLayoutCursor.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)

        mLayoutCameraView.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutCameraView, layoutParamsCameraView)

        mainThreadHandler.post {
//            mServiceCamera = Camera.open()
            mServiceCamera = getAvailableFrontCamera()
            mSurfaceView = mLayoutCameraView.findViewById(R.id.camera_view_surface_view)

            //imageview koj je sluzio za skuzit koj kurac krasni se desava sa rotacijom
//            cameraImageView = mLayoutCameraView.findViewById(R.id.camera_view_image_view)

            mSurfaceHolder = mSurfaceView?.holder
            mSurfaceHolder?.addCallback(this)
            mSurfaceHolder?.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

            mLayoutCameraView.visibility = VISIBLE
            windowManager.updateViewLayout(mLayoutCameraView, layoutParamsCameraView)

            openMServiceCamera()

            // FRONT CAMERA NOT WORKING WITH ML KIT ALSO I HAVE SERIOUS DEPRESSION
            // https://stackoverflow.com/questions/63050697/mlkit-face-detection-not-working-with-front-camera-for-android
        }
    }

    private fun hideCursor() {
        mLayoutCursor.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)

        mLayoutCameraView.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutCameraView, layoutParamsCameraView)
        mServiceCamera?.release()
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


// vito prvi pokusaj

/*

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeByteArray
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

val mainHandler = Handler(Looper.getMainLooper())
val bgScheduler = Executors.newSingleThreadScheduledExecutor()


private lateinit var mLayoutCursor: FrameLayout
private lateinit var mLayoutProgress: FrameLayout
private lateinit var windowManager: WindowManager
private lateinit var layoutParamsCursor: WindowManager.LayoutParams
private lateinit var layoutParamsProgress: WindowManager.LayoutParams

typealias LumaListener = (luma: Double) -> Unit
typealias faceOrientationListener = (faceOrientation: FaceOrientation) -> Unit


private var inputData: String? = null

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private var roll: Float = 0F
private var pitch: Float = 0F

private var pointer = Pointer()
private var pointerLast = pointer.copy()

private var clickTimer = 0

private var isCursorActivated: Boolean = false

class MyAccessibilityService : AccessibilityService(), SensorEventListener, Camera.PictureCallback {
    private lateinit var sensorManager: SensorManager
    private var mLight: Sensor? = null

    private var imageCapture: ImageCapture? = null


    private lateinit var cameraExecutor: ExecutorService

    var mCamera: Camera? = null

    override fun onInterrupt() {
        Log.i("MainService", "onInterrupt() called")
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }



    override fun onServiceConnected() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mLight?.also { light ->
            Log.i("MainServiceSensor", "register listener")
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permissions
//        startCamera()


        Log.i("MainService", "onServiceConnected() called")

//        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
//        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        Log.i("MainService", "H: " + screenHeight + " W: " + screenWidth)

//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        initPointer()
        initProgressBar()

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


//        val cursorImg: ImageView = mLayoutCursor.findViewById(R.id.cursor_image)
        val progressBar: ProgressBar = mLayoutProgress.findViewById(R.id.progressBar)

/*
        val btn: ImageButton = mLayoutCursor.findViewById(R.id.action_button)
        btn.setOnClickListener {
            Log.i("MainService", " kliko san botun")
//            val res: Boolean = dispatchGesture(createClick(5f,5f), callback, null) //bitno
//            Log.i("MainService", "result:   " + res.toString())

//            movePointer()

        }
*/
        //startServer()

        detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())

        openCameraAndDoShit()


        /*
        val mPicture = Camera.PictureCallback { data, _ ->
            Log.i("camera_logggggggg_callback", data.size.toString())
        }
        */



        bgScheduler.scheduleAtFixedRate({
            //This boolean is used as app crashes while writing images to file if simultaneous calls are made to takePicture
            if (safeToCapture) {
                //mCamera?.startPreview()
                mCamera?.takePicture(null, null, this@MyAccessibilityService)
            }
        }, 0, 50, TimeUnit.MILLISECONDS)


        mainHandler.post(object : Runnable {
            override fun run() {
                Log.i("MainService", "uso u run")

                // tu je bio

                //Thread.sleep(33)
                if(isCursorActivated){
                    pointerLast = pointer.copy()
                    pointer.translateCommands(roll, pitch)

                    movePointer(pointer)

                    if (pointer.x == pointerLast.x && pointer.y == pointerLast.y){
                        clickTimer += 2
                        progressBar.progress = clickTimer
                        if (clickTimer == 90){
                            clickTimer = 0
                            val res: Boolean = dispatchGesture(createClick(pointer.x.toFloat()-1, pointer.y.toFloat()+ screenHeight/2 -1), callback, null) //bitno
                            Log.i("MainService", "result:   " + res.toString())
                        }
                    }else{
                        clickTimer = 0
                        progressBar.progress = 0
                    }
                    //movePointer(roll,pitch)
                }else{
                    clickTimer = 0
                    progressBar.progress = 0
                }

                mainHandler.postDelayed(this, 33)
            }
        })

//        val img = BitmapFactory.decodeStream(applicationContext.assets.open("face.jpg"))



    }

    private lateinit var detector: FaceDetector

    private var safeToCapture: Boolean = true


    // basically init camera?
    private fun openCameraAndDoShit() {
        mCamera = getAvailableFrontCamera() // globally declared instance of camera
        if (mCamera == null) {
            try {
                mCamera = Camera.open() //Take rear facing camera only if no front camera available
            } catch (e: Throwable) {
                Log.d("vito_log", "camera open error: $e")
            }
        }
        try {
            val sv = SurfaceView(applicationContext)
            val surfaceTexture = SurfaceTexture(10)
            mCamera?.setPreviewTexture(surfaceTexture)

            //mCamera.setPreviewDisplay(sv.getHolder());
            val parameters = mCamera?.parameters

            //set camera parameters
            mCamera?.parameters = parameters

            //This boolean is used as app crashes while writing images to file if simultaneous calls are made to takePicture
            if (safeToCapture) {
                //mCamera?.startPreview()
                mCamera?.takePicture(null, null, this)
            }

            //Get a surface
            val sHolder = sv.holder
            //tells Android that this surface will have its data constantly replaced
            sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: Throwable) {
            Log.d("vito_log", "asdasd: $e")
        }
    }

    override fun onPictureTaken(data: ByteArray?, camera: Camera?) {
        safeToCapture = false
        Log.i("camera_logggggggg_drugiCallback", data?.size.toString())
        mainHandler.post {

        }
        // TODO analyze
        val img: Bitmap = decodeByteArray(data, 0, data!!.size)
//        val img: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        img.copyPixelsFromBuffer(ByteBuffer.wrap(data!!))
//
        Log.i("FACE", img.toString())
        val image = InputImage.fromBitmap(img, 0)
        Log.i("FACE", image.height.toString() + image.width.toString())
        Log.i("FACE", image.toString())
        val result = detector.process(image)
            .addOnSuccessListener { faces ->

                Log.d("FACE", faces.size.toString())
                if(faces.size > 0){
//                    listener(FaceOrientation(faces[0].headEulerAngleY, faces[0].headEulerAngleZ))
                    Log.d("vito_log", "more than 0 faces")
                } else {
                    Log.d("vito_log", "<= 0 faces")
                }
//                        faces[0].headEulerAngleY

            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
                Log.d("FACE", "error boy: $e")
            }

        safeToCapture = true //Set a boolean to true again after saving file.
    }

    private fun getAvailableFrontCamera(): Camera? {
        var cameraCount = 0
        var cam: Camera? = null
        val cameraInfo: Camera.CameraInfo = Camera.CameraInfo()
        cameraCount = Camera.getNumberOfCameras()
        for (camIdx in 0 until cameraCount) {
            Camera.getCameraInfo(camIdx, cameraInfo)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx)
                } catch (e: RuntimeException) {
                    Log.e(
                        "CAMERA",
                        "Camera failed to open: " + e.localizedMessage
                    )
                }
            }
        }
        return cam
    }

    /*

    private fun openCameraAndDoShit() {
        var mCamera = getAvailableFrontCamera() // globally declared instance of camera
        if (mCamera == null) {
            try {
                mCamera = Camera.open() //Take rear facing camera only if no front camera available
            } catch (e: Throwable) {
                Log.d("vito_log", "camera open error: $e")
            }
        }
        try {
            val sv = SurfaceView(applicationContext)
            val surfaceTexture = SurfaceTexture(10)
            mCamera?.setPreviewTexture(surfaceTexture)

            //mCamera.setPreviewDisplay(sv.getHolder());
            val parameters = mCamera?.parameters

            //set camera parameters
            mCamera?.parameters = parameters

            //This boolean is used as app crashes while writing images to file if simultaneous calls are made to takePicture
            if (safeToCapture) {
                mCamera?.startPreview()
                mCamera?.takePicture(null, null, this)
            }
            //Get a surface
            val sHolder = sv.holder
            //tells Android that this surface will have its data constantly replaced
            sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: Throwable) {
            Log.d("vito_log", "asdasd: $e")
        }
    }

    */



    // TODO -------------------------------------------------------------------------------------
    // below this line is code we might use but never actually will, F
    companion object {
        private const val TAG = "CameraXBasic"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private class YourImageAnalyzer(private val listener: faceOrientationListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                // ...
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())
                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        // Task completed successfully
                        // ...
                        Log.d("FACE", faces.size.toString())
                        if(faces.size > 0){
                            listener(FaceOrientation(faces[0].headEulerAngleY, faces[0].headEulerAngleZ))
                        }
//                        faces[0].headEulerAngleY

                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                        Log.d("FACE", "error boy: $e")
                    }
                    .addOnCompleteListener {
                        mediaImage.close()
                        imageProxy.close() }


            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
/*
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }
*/
            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer{faceOrientation ->
                        Log.d("AAAAAAA", "penis1: ${faceOrientation.orientationY}  penis2: ${faceOrientation.orientationZ}")
                    })
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(this,cameraSelector,)
/*
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
*/
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }




    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
        Log.i("MainServiceSensor", "accuracy changed to: " + accuracy.toString())
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // Do something with this sensor value.
        Log.i("MainServiceSensor", x.roundToInt().toString() + " " + y.roundToInt().toString() + " " + z.roundToInt().toString())
    }



    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        Log.i("MainService", "onAccessibilityEvent() called")
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        var data = ""
        if (intent?.getExtras()?.containsKey("toggle")!!)
            data = intent.getStringExtra("toggle")
        Log.i("MainService", data)
        if (data == "on"){
            isCursorActivated = true
            showCursor()
        }else if (data == "off"){
            isCursorActivated = false
            hideCursor()
        }
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
        mLayoutCursor = FrameLayout(this)
        layoutParamsCursor = WindowManager.LayoutParams()
        layoutParamsCursor.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsCursor.format = PixelFormat.TRANSLUCENT
        layoutParamsCursor.flags = layoutParamsCursor.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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

    private fun initProgressBar(){
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutProgress = FrameLayout(this)
        layoutParamsProgress = WindowManager.LayoutParams()
        layoutParamsProgress.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsProgress.format = PixelFormat.TRANSLUCENT
        layoutParamsProgress.flags = layoutParamsProgress.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsProgress.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.gravity = Gravity.START
        layoutParamsProgress.x = 0
        layoutParamsProgress.y = screenHeight/2
        mLayoutProgress.visibility = INVISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.progress_bar, mLayoutProgress)
        windowManager.addView(mLayoutProgress, layoutParamsProgress)
    }

    private fun movePointer(pointer: Pointer) {

        layoutParamsCursor.x = pointer.x
        layoutParamsCursor.y = pointer.y
        Log.i("MainService", pointer.x.toString() + " " + pointer.y.toString())

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

    }

    private fun movePointerRandom(x: Float, y: Float) {
        Log.i("MainService", x.toString() + " " + y.toString())
        val x = Random().nextInt(screenWidth)
        val y = Random().nextInt(screenHeight) - screenHeight / 2

        layoutParamsCursor.x = x
        layoutParamsCursor.y = y

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

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
                        pitch = command[0].toFloat() //mozda obrnuto !!
                        roll = command[1].toFloat() //mozda obrnuto !!
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

    fun showCursor(){
        mLayoutCursor.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

    fun hideCursor(){
        mLayoutCursor.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

}



 */


// prije vita
/*

package com.example.gyro_pointer

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

val mainHandler = Handler(Looper.getMainLooper())


private lateinit var mLayoutCursor: FrameLayout
private lateinit var mLayoutProgress: FrameLayout
private lateinit var windowManager: WindowManager
private lateinit var layoutParamsCursor: WindowManager.LayoutParams
private lateinit var layoutParamsProgress: WindowManager.LayoutParams

typealias LumaListener = (luma: Double) -> Unit
typealias faceOrientationListener = (faceOrientation: FaceOrientation) -> Unit


private var inputData: String? = null

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private var roll: Float = 0F
private var pitch: Float = 0F

private var pointer = Pointer()
private var pointerLast = pointer.copy()

private var clickTimer = 0

private var isCursorActivated: Boolean = false

class MyAccessibilityService : AccessibilityService(), SensorEventListener, LifecycleOwner {
    private lateinit var sensorManager: SensorManager
    private var mLight: Sensor? = null

    private var imageCapture: ImageCapture? = null


    private lateinit var cameraExecutor: ExecutorService


    override fun onInterrupt() {
        Log.i("MainService", "onInterrupt() called")
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }



    override fun onServiceConnected() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mLight?.also { light ->
            Log.i("MainServiceSensor", "register listener")
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permissions
        startCamera()


        Log.i("MainService", "onServiceConnected() called")

//        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
//        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        Log.i("MainService", "H: " + screenHeight + " W: " + screenWidth)

//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        initPointer()
        initProgressBar()

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


        val cursorImg: ImageView = mLayoutCursor.findViewById(R.id.cursor_image)
        val progressBar: ProgressBar = mLayoutProgress.findViewById(R.id.progressBar)

/*
        val btn: ImageButton = mLayoutCursor.findViewById(R.id.action_button)
        btn.setOnClickListener {
            Log.i("MainService", " kliko san botun")
//            val res: Boolean = dispatchGesture(createClick(5f,5f), callback, null) //bitno
//            Log.i("MainService", "result:   " + res.toString())

//            movePointer()

        }
*/
        //startServer()

        mainHandler.post(object : Runnable {
            override fun run() {
                Log.i("MainService", "uso u run")
                //Thread.sleep(33)
                if(isCursorActivated){
                    pointerLast = pointer.copy()
                    pointer.translateCommands(roll, pitch)

                    movePointer(pointer)

                    if (pointer.x == pointerLast.x && pointer.y == pointerLast.y){
                        clickTimer += 2
                        progressBar.progress = clickTimer
                        if (clickTimer == 90){
                            clickTimer = 0
                            val res: Boolean = dispatchGesture(createClick(pointer.x.toFloat()-1, pointer.y.toFloat()+ screenHeight/2 -1), callback, null) //bitno
                            Log.i("MainService", "result:   " + res.toString())
                        }
                    }else{
                        clickTimer = 0
                        progressBar.progress = 0
                    }
                    //movePointer(roll,pitch)
                }else{
                    clickTimer = 0
                    progressBar.progress = 0
                }

                mainHandler.postDelayed(this, 33)
            }
        })

        //windowManager.removeView(mLayoutCursor)
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private class YourImageAnalyzer(private val listener: faceOrientationListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                // ...
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())
                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        // Task completed successfully
                        // ...
                        Log.d("FACE", faces.size.toString())
                        if(faces.size > 0){
                            listener(FaceOrientation(faces[0].headEulerAngleY, faces[0].headEulerAngleZ))
                        }
//                        faces[0].headEulerAngleY

                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                        Log.d("FACE", "error boy: $e")
                    }
                    .addOnCompleteListener {
                        mediaImage.close()
                        imageProxy.close() }


            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
/*
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }
*/
            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer{faceOrientation ->
                        Log.d("AAAAAAA", "penis1: ${faceOrientation.orientationY}  penis2: ${faceOrientation.orientationZ}")
                    })
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this,cameraSelector,)
                /// rip here brother
/*
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
*/
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }




    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
        Log.i("MainServiceSensor", "accuracy changed to: " + accuracy.toString())
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // Do something with this sensor value.
        Log.i("MainServiceSensor", x.roundToInt().toString() + " " + y.roundToInt().toString() + " " + z.roundToInt().toString())
    }



    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        Log.i("MainService", "onAccessibilityEvent() called")
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        var data = ""
        if (intent?.getExtras()?.containsKey("toggle")!!)
            data = intent.getStringExtra("toggle")
        Log.i("MainService", data)
        if (data == "on"){
            isCursorActivated = true
            showCursor()
        }else if (data == "off"){
            isCursorActivated = false
            hideCursor()
        }
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
        mLayoutCursor = FrameLayout(this)
        layoutParamsCursor = WindowManager.LayoutParams()
        layoutParamsCursor.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsCursor.format = PixelFormat.TRANSLUCENT
        layoutParamsCursor.flags = layoutParamsCursor.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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

    private fun initProgressBar(){
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mLayoutProgress = FrameLayout(this)
        layoutParamsProgress = WindowManager.LayoutParams()
        layoutParamsProgress.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        layoutParamsProgress.format = PixelFormat.TRANSLUCENT
        layoutParamsProgress.flags = layoutParamsProgress.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParamsProgress.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParamsProgress.gravity = Gravity.START
        layoutParamsProgress.x = 0
        layoutParamsProgress.y = screenHeight/2
        mLayoutProgress.visibility = INVISIBLE

        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.progress_bar, mLayoutProgress)
        windowManager.addView(mLayoutProgress, layoutParamsProgress)
    }

    private fun movePointer(pointer: Pointer) {

        layoutParamsCursor.x = pointer.x
        layoutParamsCursor.y = pointer.y
        Log.i("MainService", pointer.x.toString() + " " + pointer.y.toString())

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

    }

    private fun movePointerRandom(x: Float, y: Float) {
        Log.i("MainService", x.toString() + " " + y.toString())
        val x = Random().nextInt(screenWidth)
        val y = Random().nextInt(screenHeight) - screenHeight / 2

        layoutParamsCursor.x = x
        layoutParamsCursor.y = y

        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

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
                        pitch = command[0].toFloat() //mozda obrnuto !!
                        roll = command[1].toFloat() //mozda obrnuto !!
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

    fun showCursor(){
        mLayoutCursor.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = VISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

    fun hideCursor(){
        mLayoutCursor.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutCursor, layoutParamsCursor)

        mLayoutProgress.visibility = INVISIBLE
        windowManager.updateViewLayout(mLayoutProgress, layoutParamsProgress)
    }

    override fun getLifecycle(): Lifecycle {
        TODO("Not yet implemented")
        Log.i("AAAAAAA", "reeeeeeeeee")
    }

}

*/

