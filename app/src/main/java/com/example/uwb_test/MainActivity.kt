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
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingSession
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
    private var start: Button? = null

    private var sendMessageBtn: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager :RangingManager? = null

    private var gattServer : BleServer? = null
    private var gattClient : BleClient?=null
    private var transportHandle : MyTransportHandle? = null

    public val setText: (ByteArray)->Unit = { data: ByteArray ->
        transportHandle?.receivedData(data)
        runOnUiThread {
            val message = data.toString(Charsets.UTF_8)
            exception?.text = message
        }
    }

    public val sendMsg: (ByteArray)->Unit = { data: ByteArray ->
        sendMessage(data)
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
        start = findViewById<Button>(R.id.ConButton)
        start!!.setOnClickListener  { _ -> connect() }
        sendMessageBtn = findViewById<Button>(R.id.MsgButton)
        sendMessageBtn!!.setOnClickListener  { _ -> sendMessage() }

        transportHandle = MyTransportHandle(sendMsg)

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


    private fun sendMessage(){

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val message = tvSendText?.text.toString()

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
        }

    }




    @SuppressLint("SetTextI18n")
    @RequiresPermission(Manifest.permission.RANGING)
    private fun connect() {
        /*val myRangingSessionCallback = MyRangingSessionCallback()
        val myExecuter = myExecutor()
        var mySession = rangingManager?.createRangingSession(myExecuter, myCallback)
        var role=0
        var config : RangingConfig*/


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


        /*
        val rangingDevice = RangingDevice.Builder().setUuid(bDev.uuids[0].uuid).build()

        val deviceHandle: DeviceHandle = DeviceHandle.Builder(rangingDevice,transportHandle)
            .build()



        if(swIsController?.isChecked == true) {
            role=RangingPreference.DEVICE_ROLE_INITIATOR
            config = OobInitiatorRangingConfig.Builder().build()
        }
        else
        {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = (OobResponderRangingConfig.Builder( deviceHandle).build()) as RangingConfig;
        }

        var rangingPreference: RangingPreference.Builder =  RangingPreference.Builder(role, config)

        //val myRangingPreference = RangingPreference.Builder();
        mySession?.start(rangingPreference.build())
        */



    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun blConnectionControllerGattClient(bMngr: BluetoothManager){
        val sc = bMngr.adapter.bluetoothLeScanner ?: return

        val myScanCallback = MyBluetoothScannerCallback()
        sc.startScan(myScanCallback)

    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public fun blConnectionControllerGattClientGotScanResult(blDevice : BluetoothDevice){

        gattClient = BleClient(baseContext,  setText )
        gattClient?.connect(blDevice)

        Log.d("TEST", "ScanResult$blDevice")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun blConnectionControleeGattServer(bMngr:BluetoothManager){
        val advertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true).setDiscoverable(true).setTimeout(10000).build()
        val advertiseData: AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true).build()
        val advertiseCallback = MyBluetoothAdvertiseCallback()
        bMngr.adapter?.bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)

        gattServer = BleServer(baseContext,  setText)
        gattServer?.init(baseContext)

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
            android.util.Log.d("ScanCallback","onScanFailed: $errorCode")
        }

        override fun onScanResult(callbackType : Int,result : ScanResult )
        {
            android.util.Log.d("ScanCallback","onScanResult: $callbackType")
        }
    }

    public class MyBluetoothAdvertiseCallback: AdvertiseCallback(){
        override fun onStartFailure(errorCode: Int) {
            android.util.Log.d("BluetoothAdvertiseCallback","onStartFailure: errorCode: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            android.util.Log.d("BluetoothAdvertiseCallback","onStartSuccess: settingsInEffect: $settingsInEffect")
        }
    }

    public class MyTransportHandle(val sendDataFunc : (ByteArray)->Unit): TransportHandle {
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
            sendDataFunc(p0)
        }

        override fun close() {
            TODO("Not yet implemented")
        }

        fun receivedData(p0: ByteArray){
            if(callbackExecuter!= null)
                callbackExecuter?.run {callbackFunction?.onReceiveData(p0)  }

        }

    }

    public class MyRangingSessionCallback: RangingSession.Callback{
        override fun onClosed(p0: Int) {
            TODO("Not yet implemented")
        }

        override fun onOpenFailed(p0: Int) {
            TODO("Not yet implemented")
        }

        override fun onOpened() {
            TODO("Not yet implemented")
        }

        override fun onResults(p0: RangingDevice, p1: RangingData) {
            TODO("Not yet implemented")
        }

        override fun onStarted(p0: RangingDevice, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun onStopped(p0: RangingDevice, p1: Int) {
            TODO("Not yet implemented")
        }

    }

    public class MyExecutor : Executor {
        override fun execute(r: Runnable) {
            r.run()
        }
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
}



