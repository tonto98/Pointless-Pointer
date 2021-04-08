package com.example.gyro_pointer

import android.content.res.Resources

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private const val factor: Int = 5
private const val cameraFactor: Int = 8

private const val X_THRESHOLD = 8
private const val Y_THRESHOLD = 8

class Pointer(x: Int = 5, y: Int = 5) {

    var x = x
    var y = y

    // this code is dumb, please fix later moron
    fun translateCommands( roll: Float, pitch: Float){
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

    fun copy(x: Int = this.x, y: Int = this.y) = Pointer(x,y)

  // roll x, pitch y
    fun translateCommandsCamera( roll: Float, pitch: Float){
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

      if (pitch > Y_THRESHOLD+2){
          if (y < screenHeight/2){
              y += cameraFactor
          }
      }
      else if (pitch < -Y_THRESHOLD-3){
          if (y > (-1* screenHeight)/2){
              y -= cameraFactor
          }
      }
    }

}