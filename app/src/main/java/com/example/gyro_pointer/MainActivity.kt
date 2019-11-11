package com.example.gyro_pointer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check for service permission
        button_service_start.setOnClickListener {
            var intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, 0)


        /*  // vjerovatno ga ne treba startat?
            //intent za startat servis?
            var intent = Intent(this, MyAccessibilityService::class.java)
            startService(intent)
        */
        }
    }
}
