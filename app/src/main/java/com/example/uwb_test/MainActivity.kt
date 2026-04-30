package com.example.uwb_test


import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingSession
import android.util.Log
import android.view.View
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

class MainActivity  : AppCompatActivity() {

    private var exception: TextView? = null

    private var tvSendText: EditText? = null
    private var tvRangeDisplay: TextView? = null
    private var swIsController: Switch? = null
    private var start: Button? = null

    private var sendMessageBtn: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager :RangingManager? = null

    private var gattServer : BleServer? = null
    private var gattClient : BleClient?=null

    public val setText: (String)->Unit = { message: String ->
        runOnUiThread {
            exception?.setText(message)
        }
    }

    //private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        rangingManager = baseContext.getSystemService(RangingManager::class.java) as RangingManager
        InitUI()
        //uwbMan = UwbManager.createInstance(baseContext)

    }




    private fun InitUI() {

        tvSendText = findViewById<EditText>(R.id.etSendText)
        tvRangeDisplay = findViewById<TextView>(R.id.rangeDisplay)
        swIsController = findViewById<Switch>(R.id.swIsController)
        exception = findViewById<TextView>(R.id.exception)
        start = findViewById<Button>(R.id.ConButton)
        start!!.setOnClickListener  { v: View? -> connect() }
        sendMessageBtn = findViewById<Button>(R.id.MsgButton)
        sendMessageBtn!!.setOnClickListener  { v: View? -> sendMessage() }

    }

    public fun setText(message:String){
        runOnUiThread {
            exception?.setText(message)
        }
    }



    private fun sendMessage(){

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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




    @RequiresPermission(Manifest.permission.RANGING)
    private fun connect() {
        val myRangingSessionCallback = myRangingSessionCallback()
        val myExecuter = myExecutor()
        //var mySession = rangingManager?.createRangingSession(myExecuter, myCallback)
        var role=0
        var config : RangingConfig


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
            exception?.setText("No Permission")
            return
            }
        }
        var bMngr : BluetoothManager = (baseContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
        var list: List<BluetoothDevice> = bMngr.adapter.bondedDevices.toList()
        var i =list.size
        exception?.setText("found $i devices")





        if(i==0)
            return
        var bDev = list[0]
        if(swIsController?.isChecked==false){

            BLConnectionControlee_GattServer(bMngr)
        }
        else {

            BLConnectionControler_GattClient(bMngr)
            BLConnectionControler_GattClient_GotScanResult(bDev)
        }


        /*
        val rangingDevice = RangingDevice.Builder().setUuid(bDev.uuids[0].uuid).build()
        val transportHandle = myTransportHandle()
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
    private fun BLConnectionControler_GattClient(bMngr: BluetoothManager){
        var SC = bMngr.adapter.getBluetoothLeScanner()
        if(SC==null)
            return

        var myScanCallback = myBluetoothScannerCallback()
        SC.startScan(myScanCallback)

    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public fun BLConnectionControler_GattClient_GotScanResult(blDevice : BluetoothDevice){

        gattClient = BleClient(baseContext,  setText )
        gattClient?.connect(blDevice)

        /*
        var myCallback = myBluetoothGattCallback()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            exception?.setText("no permission")
            return
        }
        var gatt = blDevice.connectGatt(baseContext, false, myCallback)*/
        Log.d("TEST", "ScanResult"+blDevice.toString())
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun BLConnectionControlee_GattServer(bMngr:BluetoothManager){
        var AS: AdvertiseSettings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true).setDiscoverable(true).setTimeout(10000).build()
        var AD: AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true).build()
        var ACB = myBluetoothAdvertiseCallback()
        bMngr.adapter?.bluetoothLeAdvertiser?.startAdvertising(AS, AD, ACB)

        gattServer = BleServer(baseContext,  setText)
        gattServer?.init(baseContext)

    }







    public inner class myBluetoothScannerCallback: ScanCallback(){
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onBatchScanResults(results : List<ScanResult> )
        {
            var i = results.size
            Log.d("ScanCallback","onBatchScanResults: $i devices")
            BLConnectionControler_GattClient_GotScanResult(results[0].device)
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

    public class myBluetoothAdvertiseCallback: AdvertiseCallback(){
        override fun onStartFailure(errorCode: Int) {
            android.util.Log.d("BluetoothAdvertiseCallback","onStartFailure: errorCode: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            android.util.Log.d("BluetoothAdvertiseCallback","onStartSuccess: settingsInEffect: $settingsInEffect")
        }
    }

    /*public class myTransportHandle: TransportHandle{
        override fun registerReceiveCallback(
            p0: Executor,
            p1: TransportHandle.ReceiveCallback
        ) {
            TODO("Not yet implemented")
        }

        override fun sendData(p0: ByteArray) {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }

    }*/

    public class myRangingSessionCallback: RangingSession.Callback
    {
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

    public class myExecutor : Executor {
        override fun execute(r: Runnable) {
            r.run()
        }
    }




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
    }
}





class BleServer(context : Context, rF:(String)->Unit) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var myDevice: BluetoothDevice? = null

    private val retFunc: (String)->Unit = rF

    public val characteristic = BluetoothGattCharacteristic(
        CHAR_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_WRITE or
                BluetoothGattCharacteristic.PERMISSION_READ
    )



    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if(newState == BluetoothProfile.STATE_CONNECTED){
                myDevice = device
                Log.d("BLE_SERVER","Connected: $device")
            }

        }



        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val received = value.toString(Charsets.UTF_8)

            retFunc(received)

            Log.d("BLE_SERVER", "Received: $received")

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val message = "Hello from Server"
            val bytes = message.toByteArray()

            gattServer?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,bytes)
        }
    }
    private var gattServer : BluetoothGattServer? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun init(context :Context) {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage( message: String) {

        val value = message.toByteArray()
        if(myDevice!=null) {
            val d:BluetoothDevice = myDevice!!
            gattServer?.notifyCharacteristicChanged(d, characteristic, false, value)
            Log.d("BLE_SERVER","MessageSend")
        }
    }
}

class BleClient(private val context: Context, rF:(String)->Unit) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val retFunc: (String) -> Unit = rF

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service.getCharacteristic(CHAR_UUID)
            //Log.d("BLE_CLIENT","discovered Services")
            // Enable notifications
            gatt.setCharacteristicNotification(characteristic, true)

            // Read initial value
            gatt.readCharacteristic(characteristic)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {

            //val message = value.toString(Charsets.UTF_8)
            //Log.d("BLE_CLIENT", "Read: $value")

        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value:ByteArray
        ) {

            val message = value.toString(Charsets.UTF_8)
            retFunc(message)
            Log.d("BLE_CLIENT", "Notify:$message")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        val service = bluetoothGatt?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHAR_UUID)

        val value = message.toByteArray()
        bluetoothGatt?.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
}