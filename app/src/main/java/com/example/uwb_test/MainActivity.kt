package com.example.uwb_test


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.ble.cs.BleCsRangingCapabilities
import android.ranging.ble.cs.BleCsRangingParams
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import android.ranging.oob.TransportHandle
import android.ranging.raw.RawInitiatorRangingConfig
import android.ranging.raw.RawRangingDevice
import android.ranging.raw.RawResponderRangingConfig
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.size
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

import java.util.UUID
import java.util.concurrent.Executor
import kotlin.experimental.and
import kotlin.time.Clock
import kotlin.time.ExperimentalTime



val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
val CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

const val SERVICE_ID = "com.example.myapp.DATA_EXCHANGE_SERVICE"

const val START_MEASUREMENT:Byte = 1
const val REQUEST_MEASUREMENT:Byte = 2
const val STOP_MEASUREMENT:Byte = 3
const val SHARED_RESULT:Byte = 4
const val DATA_PACKAGE :Byte = 0b00001000


open class MainActivity  : AppCompatActivity() {

    private var exception: TextView? = null
    private var tvRangeDisplay: TextView? = null


    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swIsController: Switch? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swMakeLog: Switch? = null
    public var connectButton: Button? = null
    private var disconnectButton: Button? = null
    private var startMeasuringButton: Button? = null
    private var stopMeasuringButton: Button? = null
    private var rgTecOOB: RadioGroup? = null
    private var rgTecRANG: RadioGroup? = null


    private var rangingManager :RangingManager? = null
    private var oobConnector : OobConnection? = null
    private var transportHandle : MyTransportHandle? = null
    private var rangingSession : RangingSession? = null

    private var capabilitiesCallback : MyRangingCapabilitiesCallback? = null
    private val availableCapabilities = MyRangingCapabilities()


    private var logEntries :  ArrayList<String>? = null


    private var oobMode = OOBTechnology.BLE
    private var rangingMode = RangingTechnology.AUTO

    private val saveLauncher = registerForActivityResult(SaveFileContract()) { uri: Uri? ->
        if (uri != null) writeContentToUri(uri)
        else             onFileSaveCancelled()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        rangingManager = baseContext.getSystemService(RangingManager::class.java) as RangingManager
        initUI()
        transportHandle = MyTransportHandle()

        capabilitiesCallback = MyRangingCapabilitiesCallback()



        //Log.d("Main",if(this.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)){"Yes wifi aware"} else {"No wifi aware"})
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
        startMeasuringButton!!.setOnClickListener  { _ -> startMeasuringBtn() }
        stopMeasuringButton = findViewById<Button>(R.id.StopMsgBtn)
        stopMeasuringButton!!.setOnClickListener  { _ -> stopMeasuringBtn() }
        rgTecOOB = findViewById<RadioGroup>(R.id.rgTechnologyOOB)
        rgTecRANG = findViewById<RadioGroup>(R.id.rgTechnologyRanging)


        findViewById<RadioButton>(R.id.rbTecOOBBLE).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)oobMode = OOBTechnology.BLE
        }
        findViewById<RadioButton>(R.id.rbTecOOBWIFIDIRECT).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)oobMode = OOBTechnology.WIFIDirect
        }

        findViewById<RadioButton>(R.id.rbTecOOBWIFIAWARE).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)oobMode = OOBTechnology.WIFIAWARE
        }
        findViewById<RadioButton>(R.id.rbTecRangAUTO).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.AUTO
        }
        findViewById<RadioButton>(R.id.rbTecRangWIFI).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.WIFI
        }
        findViewById<RadioButton>(R.id.rbTecRangUWB).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.UWB
        }
        findViewById<RadioButton>(R.id.rbTecRangBLE).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.BLE
        }
        findViewById<RadioButton>(R.id.rbTecRangBLERAW).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.BLE_RAW
        }

    }

    private fun stopMeasuringBtn()
    {
        oobConnector?.stopMeasuring()
        stopMeasuring()
    }
    private fun stopMeasuring(){

        if(rangingSession!=null){
            rangingSession?.stop()
        }
        else{
            runOnUiThread {
                stopMeasuringButton?.isEnabled = false
                startMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = true

                swMakeLog?.isEnabled = true
                toggleRadioGroup(rgTecRANG!!,true)
            }
            if(swMakeLog?.isChecked == true && logEntries!=null)
            {
                safeLog()
            }
        }
    }

    private fun startMeasuringBtn(){
        if(swIsController?.isChecked==true){
            oobConnector?.requestMeasuring()
        }
        else{
            startMeasuring()
        }
    }

    private fun startedMeasuring(){
        runOnUiThread {
            startMeasuringButton?.isEnabled = false
            stopMeasuringButton?.isEnabled = true
            disconnectButton?.isEnabled = false
            toggleRadioGroup(rgTecRANG!!,false)
            swMakeLog?.isEnabled = false
        }
    }

    @SuppressLint("NewApi", "MissingPermission")
    private fun startMeasuring(){
        if(rangingMode != RangingTechnology.BLE_RAW) {
            val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
            if ((rangingMode == RangingTechnology.BLE || rangingMode == RangingTechnology.AUTO) && availableCapabilities.BLE_CS) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if ((rangingMode == RangingTechnology.UWB || rangingMode == RangingTechnology.AUTO) && availableCapabilities.UWB) {
                permissions.add(Manifest.permission.UWB_RANGING)
            }
            if ((rangingMode == RangingTechnology.WIFI || rangingMode == RangingTechnology.AUTO) && availableCapabilities.WIFI_RTT) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            if(!askPermissions(this, *permissions.toTypedArray()))
            {
                return
            }

            val myRangingSessionCallback = MyRangingSessionCallback()
            val myExecutor = MyExecutor()
            rangingSession = rangingManager?.createRangingSession(myExecutor, myRangingSessionCallback)
            var role: Int
            var config: RangingConfig

            val rangingDevice = RangingDevice.Builder().build()

            val deviceHandle: DeviceHandle = DeviceHandle.Builder(rangingDevice, transportHandle!!)
                .build()
            val filter: Set<Int> = when (rangingMode) {
                RangingTechnology.BLE -> setOf(RangingManager.BLE_CS)
                RangingTechnology.WIFI -> setOf(RangingManager.WIFI_NAN_RTT)
                RangingTechnology.UWB -> setOf(RangingManager.UWB)
                else -> setOf(
                    RangingManager.BLE_CS,
                    RangingManager.WIFI_NAN_RTT,
                    RangingManager.UWB,
                )
            }


            if (swIsController?.isChecked == true) {
                role = RangingPreference.DEVICE_ROLE_INITIATOR
                config = OobInitiatorRangingConfig.Builder().addDeviceHandle(deviceHandle).setRangingTechnologyFilter(filter).build()

            } else {
                role = RangingPreference.DEVICE_ROLE_RESPONDER
                config = OobResponderRangingConfig.Builder(deviceHandle).build()
            }
            startMeasuring2(config,role)
        }
        else{
            try{
                val address = (oobConnector as BLESuper).getAddress()
                //Log.d("RawRanging","Other Address: $address; ${address?.toByteArray()}")
                startRawSessionForAddress(address!!.toByteArray())
            }
            catch(e:Exception){
                runOnUiThread{
                    exception?.text = "No BLE device found"
                }
            }
        }
    }
    private fun startRawSessionForAddress(data:ByteArray){
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)


        if(!askPermissions(this, *permissions.toTypedArray()))
        {
            return
        }


        val myRangingSessionCallback = MyRangingSessionCallback()
        val myExecutor = MyExecutor()
        rangingSession = rangingManager?.createRangingSession(myExecutor, myRangingSessionCallback)

        val address :String = data.decodeToString()
        Log.d("RawRanging","Recieved Address: $address; $data")
        if(!BluetoothAdapter.checkBluetoothAddress(address)){
            return
        }
        var role: Int
        var config: RangingConfig
        val rangingDevice: RangingDevice = RangingDevice.Builder().build()
        val BLECSParams = BleCsRangingParams.Builder(address)
            .setLocationType(BleCsRangingParams.LOCATION_TYPE_INDOOR)
            .setRangingUpdateRate(RawRangingDevice.UPDATE_RATE_NORMAL)
            .setSecurityLevel(BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE)
            .setSightType(BleCsRangingParams.SIGHT_TYPE_LINE_OF_SIGHT)
            .build()

        val rawDevice = RawRangingDevice.Builder().setCsRangingParams(BLECSParams).setRangingDevice(rangingDevice).build()

        if (swIsController?.isChecked == true) {
            role = RangingPreference.DEVICE_ROLE_INITIATOR
            config = RawInitiatorRangingConfig.Builder().addRawRangingDevice(rawDevice).build()
            Log.d("rawRanging","I AM INITIATOR")

        } else {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = RawResponderRangingConfig.Builder().setRawRangingDevice(rawDevice).build()
            Log.d("rawRanging","I AM RESPONDER")
        }
        startMeasuring2(config,role)
    }

    private fun startMeasuring2(config : RangingConfig,role :Int){
        runOnUiThread {
            tvRangeDisplay?.text = "0.000m"
        }
        val rangingPreference: RangingPreference =  RangingPreference.Builder(role, config).build()
        rangingSession?.start(rangingPreference)
        /*if(IS_RAW_MODE){
            if(swIsController?.isChecked == true)
                startMeasuring()
        }
        else*/
        if(swIsController?.isChecked == false)//normaly false in case of only OOB
        {
            oobConnector?.startMeasuring()
        }

        if(swMakeLog?.isChecked == true){
            logEntries = ArrayList<String>()
        }
    }



    @SuppressLint("SetTextI18n")
    private fun disconnect(){
        if (!askPermissions(this, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT)))
            {exception?.text = "No Permission";return}

        if(oobConnector!=null ) {
            oobConnector?.disconnect()
        }
    }
    private fun disconnected(){
        runOnUiThread {
            connectButton?.isEnabled = true
            disconnectButton?.isEnabled = false
            startMeasuringButton?.isEnabled = false
            stopMeasuringButton?.isEnabled = false
            swIsController?.isEnabled = true
            toggleRadioGroup(rgTecOOB!!,true)
            exception?.text =""
        }
    }

    private fun connect() {

        if(oobMode==OOBTechnology.WIFIDirect){
            oobConnector = WiFiDirect(this,transportHandle as OobConnectionCallback,!(swIsController!!.isChecked))
        }
        else if(oobMode == OOBTechnology.BLE) {
            oobConnector = if (swIsController?.isChecked == false) {
                BleServer(this, transportHandle as OobConnectionCallback)
            } else {
                BleClient(this,transportHandle as OobConnectionCallback,connectButton!!)
            }
        }
        else if(oobMode==OOBTechnology.WIFIAWARE){
            oobConnector = if (swIsController?.isChecked == false) {
                WiFiAwareServer(this, transportHandle as OobConnectionCallback)
            } else {
                WiFiAwareClient(this,transportHandle as OobConnectionCallback)
            }
        }
        connectButton?.isEnabled=false
        disconnectButton?.isEnabled=true
        swIsController?.isEnabled = false
        toggleRadioGroup(rgTecOOB!!,false)
    }

    @OptIn(ExperimentalTime::class)
    public fun logEntry(msg:String){
        if(logEntries==null)
            return
        var s = "[${dateTimeString()}]:"
        s+= msg
        s+= "\n"
        logEntries?.add(s)
    }

    public fun safeLog(){
        if(logEntries==null || logEntries?.size==0) {
            onFileSaveCancelled()
            return
        }
        saveLauncher.launch(
            SaveFileInput(
                suggestedFileName = "rangingLog${dateTimeString()}.txt",
                mimeType          = androidx.media3.common.MimeTypes.TEXT_VTT
            )
        )
    }

    @SuppressLint("DefaultLocale")
    public fun gotResult(data:Double){
        runOnUiThread {
            tvRangeDisplay?.text = buildString {
                append(String.format("%.3f", data))
                append("m")
            }
        }
        if(swMakeLog?.isChecked==true ){
            logEntry(data.toString())
        }
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
            Log.d("TransportHandle","sending message $s")
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
            rangingManager?.registerCapabilitiesCallback(MyExecutor(),capabilitiesCallback!!)
        }

        override fun connectionClosed() {
            Log.d("TransportHandle","connection closed")
            rangingSession?.close()
            oobConnector?.destroy()
            oobConnector = null
            disconnected()
            rangingManager?.unregisterCapabilitiesCallback(capabilitiesCallback!!)

        }

        override fun messageReceived(data: ByteArray) {
            val s = byteToHexString(data)
            Log.d("TransportHandle","message Received $s")
            if(callbackExecuter!= null)
                callbackExecuter?.run {callbackFunction?.onReceiveData(data)  }
        }

        override fun startMeasuringOrder() {
            startMeasuring()
        }

        override fun requestMeasuring() {
            startMeasuringBtn()
        }

        override fun stopMeasuring() {
            if(rangingSession!=null)
                rangingSession?.close()
        }

        override fun sharedResult(distance: Double) {
            gotResult(distance)
        }

        override fun statusMessage(message: String) {
            runOnUiThread{
                exception?.text = message
            }
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
            runOnUiThread{
                exception?.text="Failed to start ranging"
            }
        }

        override fun onOpened() {
            Log.d("RangingResult","onOpened")
            startedMeasuring()
        }

        override fun onResults(p0: RangingDevice, p1: RangingData) {
            Log.d("RangingResult","onResults: $p1")
            val distance = p1.distance?.measurement
            if(distance!=null) {
                gotResult(distance)
                oobConnector?.sharedResult(distance)
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

    public inner class MyRangingCapabilitiesCallback:RangingManager.RangingCapabilitiesCallback{
        override fun onRangingCapabilities(p0: android.ranging.RangingCapabilities) {
            Log.d("RangingCapa", "Capabilities: $p0")
            availableCapabilities.BLE_CS = p0.csCapabilities != null
            availableCapabilities.UWB = p0.uwbCapabilities != null
            availableCapabilities.WIFI_RTT = p0.rttRangingCapabilities != null
            /*val bleCsSupported = p0.csCapabilities != null
            val uwbSupported = p0.uwbCapabilities != null
            Log.d("RangingCapa", "BLE_CS Supported: $bleCsSupported, UWB Supported: $uwbSupported")
            
            if (rangingMode == RangingTechnology.BLE && !bleCsSupported) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "BLE CS not supported on this hardware", Toast.LENGTH_LONG).show()
                }
            }*/
        }
    }
    public inner class MyRangingCapabilities{
        var BLE_CS:Boolean = false
        //var BLE_RSSI:Boolean = false
        var UWB:Boolean = false
        var WIFI_RTT:Boolean = false
        //var WIFI_PD :Boolean = false
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
        abstract fun startMeasuring()
        abstract fun requestMeasuring()
        abstract fun stopMeasuring()
        abstract fun sharedResult(distance: Double)
        fun destroy(){}

    }

    public interface OobConnectionCallback{
        abstract fun connectionEstablished()
        abstract fun connectionClosed()
        abstract fun messageReceived(data:ByteArray)
        abstract fun startMeasuringOrder()
        abstract fun requestMeasuring()
        abstract fun stopMeasuring()
        abstract fun sharedResult(distance: Double)
        abstract fun statusMessage(message:String)
    }

    @OptIn(ExperimentalTime::class)
    public fun dateTimeString():String{
        val time = Clock.System.now()
        val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
        val format = LocalDateTime.Format {
            date(LocalDate.Formats.ISO)
            char(';')
            hour(); char(':'); minute(); char(':'); second()
            char('.'); secondFraction(3)
        }
        return localTime.format(format)
    }

    data class SaveFileInput(
        val suggestedFileName: String,
        val mimeType: String = "*/*"
    )

    class SaveFileContract : ActivityResultContract<SaveFileInput, Uri?>() {

        override fun createIntent(context: Context, input: SaveFileInput): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = input.mimeType
                putExtra(Intent.EXTRA_TITLE, input.suggestedFileName)
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }


    fun onFileSaveCancelled() {
        runOnUiThread {
            Toast.makeText(this, "Save cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeContentToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Replace this with whatever bytes / text you want to save:
                var fileText = ""
                for(s in logEntries!!){
                    fileText += s
                }
                outputStream.write(fileText.toByteArray())
            }
            runOnUiThread{Toast.makeText(this, "File saved!", Toast.LENGTH_SHORT).show()}
        } catch (e: Exception) {
            runOnUiThread{Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()}
        }
        logEntries = null
    }


    enum class OOBTechnology{
        BLE,WIFIDirect,WIFIAWARE
    }
    enum class RangingTechnology{
        AUTO,WIFI,BLE,UWB,BLE_RAW
    }

    companion object {
        fun askPermissions(context: Context, vararg permissions: String): Boolean {
            val activity = context as? Activity
            var allGranted = true
            val missingPermissions = mutableListOf<String>()
            Log.d("Permissions","Checking permissions: ${permissions.joinToString()}")
            if(permissions.isEmpty())return true
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    missingPermissions.add(permission)
                }
            }

            if (missingPermissions.isNotEmpty()) {
                if (activity != null) {
                    Log.d("Permissions","Asking permissions: ${missingPermissions.joinToString()}")
                    ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1)
                } else {
                    Log.e("Permissions", "Cannot request permissions from a non-Activity context: $context")
                }
            }
            allGranted = true
            for (permission in missingPermissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false

                }
            }

            return allGranted
        }

        fun toggleRadioGroup(rg:RadioGroup,enabled:Boolean){
            for (i in 0 until rg.size){
                rg.getChildAt(i).isEnabled = enabled
            }
        }
        fun byteToHexString(data:ByteArray):String{
            var s = ""
            for(b in data)
            {
                s+= Bit4ToHex((b.toInt() shr 4).toByte())
                s+= Bit4ToHex(b)
                s+=';'
            }
            return s
        }
        fun Bit4ToHex(data:Byte):Char{
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
        fun doubleToByteArray(value: Double): ByteArray {
            val bits = java.lang.Double.doubleToLongBits(value)
            return ByteArray(8) { i -> (bits shr (56 - i * 8)).toByte() }
        }
        fun byteArrayToDouble(bytes: ByteArray): Double {
            require(bytes.size == 8)
            val bits = bytes.foldIndexed(0L) { i, acc, b ->
                acc or ((b.toLong() and 0xFF) shl (56 - i * 8))
            }
            return java.lang.Double.longBitsToDouble(bits)
        }
    }


}



