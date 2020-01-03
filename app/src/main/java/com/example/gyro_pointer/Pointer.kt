package com.example.gyro_pointer

import android.content.res.Resources

private val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
private val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

private val factor: Int = 5

class Pointer(x: Int = 5, y: Int = 5) {

    var x = x
    var y = y

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




}