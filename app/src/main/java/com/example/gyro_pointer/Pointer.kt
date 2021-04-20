package com.example.gyro_pointer

import android.content.res.Resources
import android.graphics.Point
import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private const val factor: Int = 8
private const val cameraFactor: Int = 8 // this should be constant for all modes

private const val X_THRESHOLD = 6
private const val Y_THRESHOLD = 6

val points = arrayListOf<Point>(
    Point(-7, -115), Point(29, -121), Point(48, -90),
    Point(0, -81), Point(22, -80), Point(30, -66),
    Point(6, -62), Point(15, -60), Point(24, -56)
)

class Pointer(x: Int = 5, y: Int = 5) {

    var x = x
    var y = y

    // this code is dumb, please fix later moron
    fun translateCommands(roll: Float, pitch: Float){
        if (roll < (-1.5) && roll > (-3.5)){
            if (x > 5){
                x -= factor
            }
        }else if (roll > 1.5 && roll < 3.5){
            if (x < screenWidth-5){
                x += factor
            }
        }else if (roll < (-3.5)){
            if (x > 5){
                x -= factor*2
            }
        }else if (roll > 3.5){
            if (x < screenWidth-5){
                x += factor*2
            }
        }

        if (pitch < (-1.5) && pitch > (-3.5)){
            if (y < screenHeight/2){
                y += factor
            }
        }else if (pitch > 1.5 && pitch < 3.5){
            if (y > (-1* screenHeight)/2){
                y -= factor
            }
        }else if (pitch < (-3.5)){
            if (y < screenHeight/2){
                y += factor*2
            }
        }else if (pitch > 3.5){
            if (y > (-1* screenHeight)/2){
                y -= factor*2
            }
        }


    }

    fun copy(x: Int = this.x, y: Int = this.y) = Pointer(x, y)

  // roll x, pitch y
    fun translateCommandsCamera(roll: Float, pitch: Float){
      if (roll > X_THRESHOLD){
          if (x < screenWidth-5){
              x += cameraFactor
          }
      }
      else if (roll < -X_THRESHOLD){
          if (x > 5){
              x -= cameraFactor
          }
      }

      if (pitch > Y_THRESHOLD+3){
          if (y < screenHeight/2){
              y += cameraFactor
          }
      }
      else if (pitch < -Y_THRESHOLD+4){
          if (y > (-1* screenHeight)/2){
              y -= cameraFactor
          }
      }
    }

    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val xSquare = (x1 - x2).pow(2.0)
        val ySquare = (y1 - y2).pow(2.0)
        return sqrt(xSquare + ySquare)
    }

    fun translateCommandsMagnet(x: Int, y: Int){
        var minDistance: Double = 999999.0
        var minIndex: Int = 0
        var index: Int = 0
        points.forEach { point ->
            val dist = calculateDistance(x.toDouble(), y.toDouble(), point.x.toDouble(), point.y.toDouble())
            if (dist < minDistance){
                minDistance = dist
                minIndex = index
            }
            index++
        }

        Log.d("Pointer", "minimum distance is $minDistance, index: $minIndex")

        if(minDistance > 18 && minIndex == 8) return

        //return

        when (minIndex){

            0 -> { // top left
                this.x -= factor
                this.y -= factor
            }
            1 -> { // top mid
                this.y -= factor
            }
            2 -> { // top right
                this.x += factor
                this.y -= factor
            }
            3 -> { // mid left
                this.x -= factor
            }
            // 4 middle
            5 -> { // mid right
                this.x += factor
            }
            6 -> { // bottom left
                this.x -= factor
                this.y += factor
            }
            7 -> { // bottom mid
                this.y += factor
            }
            8 -> {
                this.x += factor
                this.y += factor
            }
        }

        // limiters
        if (this.x > screenWidth-5){
            this.x = screenWidth-5
        }
        if (this.x < 5){
            this.x = 5
        }
        if (this.y > screenHeight/2){
            this.y = screenHeight/2
        }
        if (this.y < (-1* screenHeight)/2){
            this.y = (-1* screenHeight)/2
        }

    }

}