package com.example.gyro_pointer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindService(Intent(this, MyAccessibilityService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        // check for service permission
        button_service_start.setOnClickListener {
//            var intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
//            startActivityForResult(intent, 0)



            var intent = Intent(this, MyAccessibilityService::class.java)
            intent.putExtra("data", "dataaaa")
            startService(intent)

            

        /*  // vjerovatno ga ne treba startat?
            //intent za startat servis?
            var intent = Intent(this, MyAccessibilityService::class.java)
            startService(intent)
        */

        }

        switch_cursor_toggle.setOnClickListener {

            if (switch_cursor_toggle.isChecked){
                Log.i("MainActivity", "isActivated")
                var intent = Intent(this, MyAccessibilityService::class.java)
                intent.putExtra("toggle", "on")
                startService(intent)
            }else{
                Log.i("MainActivity", "isDeactivated")
                var intent = Intent(this, MyAccessibilityService::class.java)
                intent.putExtra("toggle", "off")
                startService(intent)
            }

        }




    }
    val mConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i("MainActivity", "Service Disconncected")
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i("MainActivity", "Service Conncected")
        }
    }


}
