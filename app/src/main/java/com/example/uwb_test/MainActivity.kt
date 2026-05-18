package com.example.uwb_test


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import android.ranging.oob.TransportHandle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.i18n.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.sql.Time

import java.util.UUID
import java.util.concurrent.Executor
import kotlin.experimental.and

val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
val CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

const val SNIPPET_RECIEVED:Byte = 0
const val SNIPPET_LAST:Byte = 1
const val SNIPPET_INTERMEDIATE:Byte  = 2

class MainActivity  : AppCompatActivity() {

    private var exception: TextView? = null

    private var tvRangeDisplay: TextView? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swIsController: Switch? = null
    private var swMakeLog: Switch? = null
    private var connectButton: Button? = null
    private var disconnectButton: Button? = null

    private var startMeasuringButton: Button? = null
    private var stopMeasuringButton: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager :RangingManager? = null

    private var oobConnector : OobConnection? = null

    private var transportHandle : MyTransportHandle? = null

    private var rangingSession : RangingSession? = null

    private var logEntries :  ArrayList<String>? = null





    //private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        rangingManager = baseContext.getSystemService(RangingManager::class.java) as RangingManager
        initUI()
        //uwbMan = UwbManager.createInstance(baseContext)

    }




    private fun initUI() {


        tvRangeDisplay = findViewById<TextView>(R.id.rangeDisplay)
        swIsController = findViewById<Switch>(R.id.swIsController)
        swMakeLog = findViewById<Switch>(R.id.swMakeLog)
        exception = findViewById<TextView>(R.id.exception)
        connectButton = findViewById<Button>(R.id.ConButton)
        connectButton!!.setOnClickListener  { _ -> connect() }
        disconnectButton = findViewById<Button>(R.id.DConButton)
        disconnectButton!!.setOnClickListener  { _ -> disconnect() }
        startMeasuringButton = findViewById<Button>(R.id.StartMsgBtn)
        startMeasuringButton!!.setOnClickListener  { _ -> startMeasuring() }
        stopMeasuringButton = findViewById<Button>(R.id.StopMsgBtn)
        stopMeasuringButton!!.setOnClickListener  { _ -> stopMeasuring() }


        transportHandle = MyTransportHandle()

    }

    private fun stopMeasuring(){

        if(rangingSession!=null){
            rangingSession?.stop()
        }
        else{

            if(swMakeLog?.isChecked == true && logEntries!=null)
            {
                //TODO
                val file = File(baseContext.filesDir, "log.txt")
                for(s in logEntries!!){
                    file.appendText(s)
                }
            }
            runOnUiThread {
                stopMeasuringButton?.isEnabled = false
                startMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = true
                swMakeLog?.isEnabled = true
            }
        }

    }
    @SuppressLint("NewApi")
    private fun startMeasuring(){
        startMeasuringButton?.isEnabled = false
        stopMeasuringButton?.isEnabled = true
        disconnectButton?.isEnabled = false

        swMakeLog?.isEnabled = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }


        val myRangingSessionCallback = MyRangingSessionCallback()
        val myExecutor = MyExecutor()
        rangingSession = rangingManager?.createRangingSession(myExecutor, myRangingSessionCallback)
        var role: Int
        var config : RangingConfig

        val rangingDevice = RangingDevice.Builder().build()

        val deviceHandle: DeviceHandle = DeviceHandle.Builder(rangingDevice,transportHandle!!)
            .build()
        val filter: Set<Int> = setOf(RangingManager.BLE_CS,RangingManager.WIFI_NAN_RTT)
        if(swIsController?.isChecked == true) {
            role= RangingPreference.DEVICE_ROLE_INITIATOR

            config = OobInitiatorRangingConfig.Builder().addDeviceHandle(deviceHandle).setRangingTechnologyFilter(filter).build()
            //config = OobInitiatorRangingConfig.Builder().addDeviceHandle(deviceHandle).build()
            if(swMakeLog?.isChecked == true){
                logEntries = ArrayList<String>()
            }
        }
        else
        {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = (OobResponderRangingConfig.Builder( deviceHandle).build())
        }
        val rangingPreference: RangingPreference =  RangingPreference.Builder(role, config).build()
        rangingSession?.start(rangingPreference)
    }



    @SuppressLint("SetTextI18n")
    private fun disconnect(){
        if ((ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
        {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN),1)
            if ((ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
            {exception?.text = "No Permission";return}
        }

        if(oobConnector!=null ) {
            oobConnector?.disconnect()
        }
    }
    private fun disconnected(){
        runOnUiThread {
            connectButton?.isEnabled = true
            disconnectButton?.isEnabled = false
            startMeasuringButton?.isEnabled = false
        }
    }



    private fun connect() {

        if(swIsController?.isChecked==false){
            oobConnector = BleServer(baseContext,  transportHandle as OobConnectionCallback)
        }
        else {
            oobConnector = BleClient(baseContext,  transportHandle as OobConnectionCallback)
        }
        connectButton?.isEnabled=false
        disconnectButton?.isEnabled=true
    }


    public inner class MyTransportHandle(): TransportHandle,OobConnectionCallback {
        var callbackExecuter : Executor? = null
        var callbackFunction : TransportHandle.ReceiveCallback? = null


        override fun registerReceiveCallback(
            p0: Executor,
            p1: TransportHandle.ReceiveCallback
        ) {
            callbackExecuter = p0
            callbackFunction = p1
        }

        override fun sendData(p0: ByteArray) {
            val s = byteToHexString(p0)
            Log.d("TransportHandle","sending Data $s")
            oobConnector?.sendMessage(p0)
        }

        override fun close() {
            Log.d("TransportHandle","close")
        }


        override fun connectionEstablished() {
            Log.d("TransportHandle","connection established")
            runOnUiThread {
                startMeasuringButton?.isEnabled=true
            }
        }

        override fun connectionClosed() {
            Log.d("TransportHandle","connection closed")
            oobConnector = null
            disconnected()
        }

        override fun messageRecieved(data: ByteArray) {
            val s = byteToHexString(data)
            Log.d("TransportHandle","message Recieved $s")
            if(callbackExecuter!= null)
                callbackExecuter?.run {callbackFunction?.onReceiveData(data)  }
        }

    }

    public inner class MyRangingSessionCallback: RangingSession.Callback{
        override fun onClosed(p0: Int) {
            Log.d("RangingResult","onClosed: $p0")
            rangingSession = null
            stopMeasuring()

        }

        override fun onOpenFailed(p0: Int) {
            Log.d("RangingResult","onOpenFailed: $p0")
            rangingSession = null
            stopMeasuring()



        }

        override fun onOpened() {
            Log.d("RangingResult","onOpened")
        }

        override fun onResults(p0: RangingDevice, p1: RangingData) {

            Log.d("RangingResult","onResults: $p1")
            runOnUiThread {
                val message = p1.distance?.measurement
                tvRangeDisplay?.text = String.format("%.3f", message)
            }
            if(swMakeLog?.isChecked==true && logEntries!=null){
                var s = ""
                val now = LocalDate.now()
                s += "[${now.format( LocalDateTime.Formats.ISO)}]:"
                s+= "${p1.distance?.measurement}\n"
                logEntries?.add(s)
            }
        }

        override fun onStarted(p0: RangingDevice, p1: Int) {
            Log.d("RangingResult","onStarted $p1")
        }

        override fun onStopped(p0: RangingDevice, p1: Int) {
            Log.d("RangingResult","onStopped $p1")
            rangingSession?.close()
        }

    }

    public class MyExecutor : Executor {
        override fun execute(r: Runnable) {
            r.run()
        }
    }

    public interface OobConnection{
        abstract fun sendMessage(data: ByteArray)
        abstract fun isInitiator():Boolean
        abstract fun disconnect()

    }

    public interface OobConnectionCallback{
        abstract fun connectionEstablished()
        abstract fun connectionClosed()
        abstract fun messageRecieved(data:ByteArray)
    }


    /*
    private fun ParseStringToInt(s: String):Int{
        var x = 0
        for(c in s.toCharArray())
        {
            if(!c.isDigit())
                return 0
            x*=10
            x+= c.minus('0')
        }
        return x
    }

    private fun ParseStringToByteArry(s: String):ByteArray{
        var x : ByteArray = ByteArray(16)

        if(s.length!=32)
            return x

        x= ByteArray(s.length/2){0}

        var i:Int=0
        var byte: Byte = 0
        while(i <s.length)
        {

            if(HexVal(s[i]) == (-1).toByte()||HexVal(s[i+1]) == (-1).toByte())
                byte=0
            else
                byte = ((HexVal(s[i]).toInt().shl(4).toByte()) + HexVal(s[i+1])).toByte()
            x[i]=byte
            i+=2
        }
        return x
    }
    private fun HexVal(c:Char): Byte{
        var x:Byte = 0
        when(c){
            '0' -> x=0
            '1' -> x=1
            '2' -> x=2
            '3' -> x=3
            '4' -> x=4
            '5' -> x=5
            '6' -> x=6
            '7' -> x=7
            '8' -> x=8
            '9' -> x=9
            'A' -> x=10
            'B' -> x=11
            'C' -> x=12
            'D' -> x=13
            'E' -> x=14
            'F' -> x=15
            'a' -> x=10
            'b' -> x=11
            'c' -> x=12
            'd' -> x=13
            'e' -> x=14
            'f' -> x=15
            else -> x=-1
        }
        return x
    }*/

    companion object {
    public fun byteToHexString(data:ByteArray):String{
        var s = ""
        for(b in data)
        {
            s+= Bit4ToHex((b.toInt() shr 4).toByte())
            s+= Bit4ToHex(b)
            s+=';'
        }
        return s
    }
    public fun Bit4ToHex(data:Byte):Char{
        val data2 :Int = (data and 0xF).toInt()
        when(data2){
            0 -> return '0'
            1 -> return '1'
            2 -> return '2'
            3 -> return '3'
            4 -> return '4'
            5 -> return '5'
            6 -> return '6'
            7 -> return '7'
            8 -> return '8'
            9 -> return '9'
            10 -> return 'A'
            11 -> return 'B'
            12 -> return 'C'
            13 -> return 'D'
            14 -> return 'E'
            15 -> return 'F'
            else -> return 'X'
        }
    }
    }
}



