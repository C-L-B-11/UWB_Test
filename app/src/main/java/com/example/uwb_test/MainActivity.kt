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

    private var tvSendText: EditText? = null
    private var tvRangeDisplay: TextView? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swIsController: Switch? = null
    private var connectButton: Button? = null
    private var disconnectButton: Button? = null

    private var startMeasuringButton: Button? = null
    private var stopMeasuringButton: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager :RangingManager? = null

    private var gattServer : BleServer? = null
    private var gattClient : BleClient?=null
    private var transportHandle : MyTransportHandle? = null

    private var rangingSession : RangingSession? = null

    val setText: (ByteArray)->Unit = { data: ByteArray ->
        receivedMessage(data)
    }

    val sendMsg: (ByteArray)->Unit = { data: ByteArray ->
        sendMessage(data)
    }
    val connectionEstablished: ()->Unit = {
        runOnUiThread {
            startMeasuringButton?.isEnabled=true
        }
    }

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

        tvSendText = findViewById<EditText>(R.id.etSendText)
        tvRangeDisplay = findViewById<TextView>(R.id.rangeDisplay)
        swIsController = findViewById<Switch>(R.id.swIsController)
        exception = findViewById<TextView>(R.id.exception)
        connectButton = findViewById<Button>(R.id.ConButton)
        connectButton!!.setOnClickListener  { _ -> connect() }
        disconnectButton = findViewById<Button>(R.id.DConButton)
        disconnectButton!!.setOnClickListener  { _ -> disconnect() }
        startMeasuringButton = findViewById<Button>(R.id.StartMsgBtn)
        startMeasuringButton!!.setOnClickListener  { _ -> startMeasuring() }
        stopMeasuringButton = findViewById<Button>(R.id.StopMsgBtn)
        stopMeasuringButton!!.setOnClickListener  { _ -> stopMeasuring() }


        transportHandle = MyTransportHandle(sendMsg)

    }

    private fun receivedMessage(data:ByteArray){
        transportHandle?.receivedData(data)
        runOnUiThread {
            val message = data.toString(Charsets.UTF_8)
            exception?.text = message
        }
    }
    private fun sendMessage(data:ByteArray){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if(swIsController?.isChecked==false){
            if(gattServer!=null){

                gattServer?.sendMessage(data)
            }
        }
        else
        {
            if(gattClient !=null){
                gattClient?.sendMessage(data)
            }
        }
    }

    private fun stopMeasuring(){

        if(rangingSession!=null){
            rangingSession?.stop()
        }
        else{
            stopMeasuringButton?.isEnabled = false
            startMeasuringButton?.isEnabled = true
            disconnectButton?.isEnabled = true

        }

    }
    @SuppressLint("NewApi")
    private fun startMeasuring(){
        startMeasuringButton?.isEnabled = false
        stopMeasuringButton?.isEnabled = true
        disconnectButton?.isEnabled = false

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        /*val message = tvSendText?.text.toString()

        if(swIsController?.isChecked==false){
            if(gattServer!=null){

                gattServer?.sendMessage(message)
            }
        }
        else
        {
            if(gattClient !=null){
                gattClient?.sendMessage(message)
            }
        }*/


        val myRangingSessionCallback = MyRangingSessionCallback()
        val myExecutor = MyExecutor()
        rangingSession = rangingManager?.createRangingSession(myExecutor, myRangingSessionCallback)
        var role: Int
        var config : RangingConfig

        val rangingDevice = RangingDevice.Builder().build()

        val deviceHandle: DeviceHandle = DeviceHandle.Builder(rangingDevice,transportHandle!!)
            .build()



        if(swIsController?.isChecked == true) {
            role= RangingPreference.DEVICE_ROLE_INITIATOR
            val filter: Set<Int> = setOf(RangingManager.BLE_CS,RangingManager.WIFI_NAN_RTT)
            config = OobInitiatorRangingConfig.Builder().addDeviceHandle(deviceHandle).setRangingTechnologyFilter(filter).build()

        }
        else
        {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = (OobResponderRangingConfig.Builder( deviceHandle).build())

        }

        val rangingPreference: RangingPreference =  RangingPreference.Builder(role, config).build()

        //val myRangingPreference = RangingPreference.Builder();
        rangingSession?.start(rangingPreference)



    }



    @SuppressLint("SetTextI18n")
    private fun disconnect(){
        if (
            (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED)
        )
        {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN),1)
            if ((ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED)
            )
            {
                exception?.text = "No Permission"
                return
            }
        }

        if(gattServer!=null && gattClient==null){
            if(gattServer?.disconnect() == true){
                gattServer=null
            }
            else{
                exception?.text = "Still has connected Devices"
                Log.d("MainActivity","Still has connected Devices")
                return
            }
        }
        else if(gattServer==null && gattClient!=null){
            gattClient!!.disconnect()
            gattClient=null
        }
        else
        {
            exception?.text = "Connection State undefined"
            Log.d("MainActivity","Connection State undefined")
            return
        }
        connectButton?.isEnabled=true
        disconnectButton?.isEnabled=false
        startMeasuringButton?.isEnabled = false
    }
    @SuppressLint("SetTextI18n")
    @RequiresPermission(Manifest.permission.RANGING)
    private fun connect() {


        if (
            (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED)
            || (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED)
            || (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
        )
        {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN),1)
            if ((ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED)
            )
            {
                exception?.text = "No Permission"
            return
            }
        }
        val bMngr : BluetoothManager = (baseContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
        val list: List<BluetoothDevice> = bMngr.adapter.bondedDevices.toList()
        val i =list.size
        exception?.text = "found $i devices"


        if(i==0)
            return
        val bDev = list[0]
        if(swIsController?.isChecked==false){

            blConnectionControleeGattServer(bMngr)
        }
        else {

            blConnectionControllerGattClient(bMngr)
            blConnectionControllerGattClientGotScanResult(bDev)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun blConnectionControllerGattClient(bMngr: BluetoothManager){
        val sc = bMngr.adapter.bluetoothLeScanner ?: return

        val myScanCallback = MyBluetoothScannerCallback()
        sc.startScan(myScanCallback)

    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public fun blConnectionControllerGattClientGotScanResult(blDevice : BluetoothDevice){

        gattClient = BleClient(baseContext,  setText , connectionEstablished)
        gattClient?.connect(blDevice)

        Log.d("TEST", "ScanResult$blDevice")

        connectButton?.isEnabled=false
        disconnectButton?.isEnabled=true

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun blConnectionControleeGattServer(bMngr:BluetoothManager){
        val advertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true).setDiscoverable(true).setTimeout(10000).build()
        val advertiseData: AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true).build()
        val advertiseCallback = MyBluetoothAdvertiseCallback()
        bMngr.adapter?.bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)

        gattServer = BleServer(baseContext,  setText,connectionEstablished)
        gattServer?.init(baseContext)
        connectButton?.isEnabled=false
        disconnectButton?.isEnabled=true

    }







    public inner class MyBluetoothScannerCallback: ScanCallback(){
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onBatchScanResults(results : List<ScanResult> )
        {
            val i = results.size
            Log.d("ScanCallback","onBatchScanResults: $i devices")
            blConnectionControllerGattClientGotScanResult(results[0].device)
        }

        override fun onScanFailed(errorCode : Int)
        {
            Log.d("ScanCallback","onScanFailed: $errorCode")
        }

        override fun onScanResult(callbackType : Int,result : ScanResult )
        {
            Log.d("ScanCallback","onScanResult: $callbackType")
        }
    }

    public class MyBluetoothAdvertiseCallback: AdvertiseCallback(){
        override fun onStartFailure(errorCode: Int) {
            Log.d("BluetoothAdvertiseCallback","onStartFailure: errorCode: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BluetoothAdvertiseCallback","onStartSuccess: settingsInEffect: $settingsInEffect")
        }
    }

    public inner class MyTransportHandle(val sendDataFunc : (ByteArray)->Unit): TransportHandle {
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
            sendDataFunc(p0)
        }

        override fun close() {
            Log.d("TransportHandle","close")
        }

        fun receivedData(p0: ByteArray){
            val s = byteToHexString(p0)
            Log.d("TransportHandle","recieved Data $s")
            if(callbackExecuter!= null)
                callbackExecuter?.run {callbackFunction?.onReceiveData(p0)  }

        }

    }

    public inner class MyRangingSessionCallback: RangingSession.Callback{
        override fun onClosed(p0: Int) {
            Log.d("RangingResult","onClosed: $p0")
            rangingSession = null
            runOnUiThread{
                stopMeasuringButton?.isEnabled = false
                startMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = true

            }

        }

        override fun onOpenFailed(p0: Int) {
            Log.d("RangingResult","onOpenFailed: $p0")
            rangingSession = null
            runOnUiThread{
                stopMeasuringButton?.isEnabled = false
                startMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = true

            }

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

    public interface oobConnection{
        abstract fun sendMessage(data: ByteArray)
        abstract fun isInitiator():Boolean
        abstract fun disconnect()
        abstract fun connect(device: BluetoothDevice)
    }

    public interface oobConnectionCallback{
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



