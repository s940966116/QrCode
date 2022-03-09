package com.example.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import java.util.*
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {


    private val permissionRequestCode = 1
    private val allpermissionRequestCode = 0
    private val smspermissionRequestCode = 2
    private lateinit var surfaceView: SurfaceView
    private lateinit var cameraSource: CameraSource
    private lateinit var barcodeDetector: BarcodeDetector
    private var is_exit by Delegates.notNull<Boolean>()
    private val requestPermission = arrayOf(Manifest.permission.CAMERA,Manifest.permission.SEND_SMS)
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var actionBar = supportActionBar
        actionBar?.hide()

        checkPermission()
        is_exit = false
        barcodeDetector = BarcodeDetector.Builder(this).setBarcodeFormats(Barcode.QR_CODE).build()
        barcodeDetector.setProcessor(object : Detector.Processor<Barcode>{
            override fun release() {
            }

            @SuppressLint("MissingPermission")
            override fun receiveDetections(p0: Detector.Detections<Barcode>) {
                val qrcodes = p0.detectedItems
                if (qrcodes.size() > 0 && !scanning){
                    var phone: String
                    var content: String

                    var data = qrcodes.valueAt(0).displayValue.trim().split("\n".toRegex(),2)
                    if(data.size == 2){
                        phone = data[0]
                        content = data[1]
                    }else{
                        phone = data[0]
                        content = ""
                    }

                    if(phone == "1922"){
                        scanning = true
                        vibrate()
                        runOnUiThread {
                            cameraSource.stop()
                            if(ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED){
                                var smsManager = SmsManager.getDefault()
                                smsManager.sendTextMessage(phone,null,content,null,null)
                                Timer().schedule(object : TimerTask(){
                                    override fun run() {
                                        scanning = false
                                    }
                                },2000)
                                cameraSource.start(surfaceView.holder)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    val uri = Uri.parse("smsto:${phone}")
                                    var smsIntent = Intent(Intent.ACTION_SENDTO,uri)
                                    smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(smsIntent)
                                }
                            }else{
                                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.SEND_SMS),smspermissionRequestCode)
                                Timer().schedule(object : TimerTask(){
                                    override fun run() {
                                        scanning = false
                                    }
                                },2000)
                            }
                        }
                    }
                }
            }
        })
        val screenInfo = getScreenSize()
        cameraSource = CameraSource.Builder(this,barcodeDetector)
            .setRequestedPreviewSize(screenInfo.getValue("height"),screenInfo.getValue("width"))
            .setAutoFocusEnabled(true)
            .build()
        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraSource.start(holder)
                }else{
                    ActivityCompat.requestPermissions(this@MainActivity,arrayOf(Manifest.permission.CAMERA),permissionRequestCode)
                }

            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraSource.stop()
            }
        })
    }

    private fun getScreenSize(): Map<String,Int>{
        val rect = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val height = rect.height()
        val width = rect.width()
        return mapOf("height" to height, "width" to width)
    }

    private fun vibrate(){
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if(vibrator.hasVibrator()){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                vibrator.vibrate(VibrationEffect.createOneShot(500,VibrationEffect.DEFAULT_AMPLITUDE))
            }else{
                vibrator.vibrate(500)
            }
        }
    }

    private fun checkPermission(){
        var needPermisson = arrayListOf<String>()
        for(request in requestPermission){
            if(ActivityCompat.checkSelfPermission(this,request) != PackageManager.PERMISSION_GRANTED){
                needPermisson.add(request)
            }
        }
        if(needPermisson.size < 1){
            return
        }
        ActivityCompat.requestPermissions(this,needPermisson.toTypedArray(),allpermissionRequestCode)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isEmpty()){
            return
        }
        if(requestCode == permissionRequestCode){
            for(result in grantResults){
                if(result == PackageManager.PERMISSION_GRANTED){
                    runOnUiThread{
                        cameraSource.start(surfaceView.holder)
                    }
                }
            }
        }else if(requestCode == allpermissionRequestCode){
            runOnUiThread{
                cameraSource.start(surfaceView.holder)
            }
        }else if(requestCode == smspermissionRequestCode){
            runOnUiThread{
                cameraSource.start(surfaceView.holder)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeDetector.release()
        cameraSource.stop()
        cameraSource.release()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if(is_exit == false){
                is_exit = true
                Toast.makeText(this,"在按一次退出",Toast.LENGTH_SHORT).show()
                Timer().schedule(object : TimerTask(){
                    override fun run() {
                        is_exit = false
                    }
                },2000)
                return true
            }else{
                finish()
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
