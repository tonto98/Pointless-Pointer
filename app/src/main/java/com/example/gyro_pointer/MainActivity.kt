package com.example.gyro_pointer

import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.Toast
import android.os.IBinder
import android.content.ComponentName
import android.content.Context
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.util.Log


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
