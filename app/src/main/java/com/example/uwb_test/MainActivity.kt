package com.example.uwb_test


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.ranging.DataNotificationConfig
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.SessionConfig
import android.ranging.ble.cs.BleCsRangingCapabilities
import android.ranging.ble.cs.BleCsRangingParams
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import android.ranging.oob.TransportHandle
import android.ranging.raw.RawInitiatorRangingConfig
import android.ranging.raw.RawRangingDevice
import android.ranging.raw.RawResponderRangingConfig
import android.ranging.uwb.UwbRangingParams
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
import android.ranging.uwb.UwbAddress
import android.ranging.uwb.UwbComplexChannel
import androidx.core.view.size
import androidx.media3.common.MimeTypes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import java.util.Locale.getDefault
import java.util.UUID

import java.util.concurrent.Executor
import kotlin.byteArrayOf
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
const val DATA_PACKAGE  :Byte = 0b00001000
const val DATA_PACKAGE2 :Byte = 0b00011000


open class MainActivity  : AppCompatActivity() {

    /**
     * Diverse UI referenzen
     */
    private var exception: TextView? = null
    private var tvRangeDisplay: TextView? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swIsController: Switch? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swMakeLog: Switch? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var swUseGNSS: Switch? = null
    public var connectButton: Button? = null
    private var disconnectButton: Button? = null
    private var startMeasuringButton: Button? = null
    private var stopMeasuringButton: Button? = null
    private var rgTecRANG: RadioGroup? = null


    private val uiMode = object {
        private var mode = UiMode.Idle

        fun startConnecting(){
            if(mode!=UiMode.Idle)
                return
            mode = UiMode.Connecting

            runOnUiThread {
                exception?.text =""
                connectButton?.isEnabled = false
                disconnectButton?.isEnabled = true
                swIsController?.isEnabled = false
            }
        }
        fun connected(){
            if(mode!=UiMode.Connecting&&mode!=UiMode.Idle)
                return
            mode = UiMode.Connected
            runOnUiThread {
                startMeasuringButton?.isEnabled=true
                connectButton?.isEnabled = false
                disconnectButton?.isEnabled = true
                swIsController?.isEnabled = false
            }
        }
        fun startedMeasuring(){
            if(mode!=UiMode.Connected)
                return
            mode = UiMode.Measuring

            runOnUiThread {
                startMeasuringButton?.isEnabled = false
                stopMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = false
                toggleRadioGroup(rgTecRANG!!,false)
                swMakeLog?.isEnabled = false
                swUseGNSS?.isEnabled = false
            }
        }
        fun stoppedMeasuring(){
            if(mode!= UiMode.Measuring)
                return
            mode = UiMode.Connected
            runOnUiThread {
                stopMeasuringButton?.isEnabled = false
                startMeasuringButton?.isEnabled = true
                disconnectButton?.isEnabled = true

                swMakeLog?.isEnabled = true
                swUseGNSS?.isEnabled = true
                toggleRadioGroup(rgTecRANG!!,true)
            }
        }
        fun disconnected(){
            if(mode == UiMode.Idle)
                return
            mode = UiMode.Idle

            runOnUiThread {
                connectButton?.isEnabled = true
                disconnectButton?.isEnabled = false
                startMeasuringButton?.isEnabled = false
                stopMeasuringButton?.isEnabled = false
                swIsController?.isEnabled = true
            }
        }
    }



    /**
    *   Objekt das die OOB Verbindung zum anderen Gerät steuert und unterhält
    */
    private var oobConnector : OobConnection? = null

    /**
     * Executor Klasse für verschiedene Callbacks
     */
    private val myExecutor = Executor { r -> r.run() }

    /**
     * Schnittstelle für die Verbindung zwischen rangingSession und oobConnector, sowie Zustandsrückmeldung zur UI
     */
    private var oobCallback = object:  OobConnectionCallback {
        /**
         * Member von OOBConnectionCallback
         */
        override fun connectionEstablished() {
            Log.d("TransportHandle","connection established")
            uiMode.connected()
            rangingManager?.registerCapabilitiesCallback(myExecutor,capabilitiesCallback)
        }
        /**
         * Member von OOBConnectionCallback
         */
        override fun connectionClosed() {
            Log.d("TransportHandle","connection closed")
            for (s in sessions.values){
                s.session?.close()
            }
            sessions.clear()
            oobConnector?.destroy()
            oobConnector = null
            uiMode.disconnected()
            rangingManager?.unregisterCapabilitiesCallback(capabilitiesCallback)

        }

        /**
         * Member von OOBConnectionCallback
         */
        override fun messageReceived(data: ByteArray) {
            val s = byteToHexString(data)
            Log.d("TransportHandle","message Received $s")

            if(data.isEmpty())
                return;
            val mode : Byte = data[0]
            var key : Byte = 0
            var realData :ByteArray = byteArrayOf()
            if(data.size>=2){
                key=data[1]
                realData = data.copyOfRange(2,data.size)
            }

            when(mode){
                START_MEASUREMENT -> startMeasuringOrder(RangingTechnology.entries[key.toInt()])
                REQUEST_MEASUREMENT -> requestMeasuring(RangingTechnology.entries[key.toInt()])
                STOP_MEASUREMENT -> stopMeasuring()
                SHARED_RESULT -> gotResult(key, byteArrayToDouble(realData))
                DATA_PACKAGE -> {
                    if(sessions.containsKey(key)){
                        sessions[key]?.handle?.receiveData(realData)
                    }
                    else
                        Log.d("OOB","Unknown key")
                }
                else ->  {
                    Log.d("OOB","Unknown message")
                }
            }
        }

        /**
         * StartMeasuring Paket wurde empfangen
         */
        fun startMeasuringOrder(mode:RangingTechnology) {
            setRangingTechnology(mode)
            startMeasuring()
        }
        /**
         * RequestMeasuring Paket wurde empfangen
         */
        fun requestMeasuring(mode:RangingTechnology) {
            setRangingTechnology(mode)
            startMeasuringBtn()
        }
        /**
         * StopMeasuring Paket wurde empfangen
         */
        fun stopMeasuring() {
            for(s in sessions.values){
                s.session?.stop()
            }
        }

        /**
         * Member von OOBConnectionCallback
         */
        override fun statusMessage(message: String) {
            runOnUiThread{
                exception?.text = message
            }
        }
    }


    inner class MultiSession(val key:Byte){
        val handle = object :  HandleCallback {
                var callbackExecuter : Executor? = null
                var callbackFunction : TransportHandle.ReceiveCallback? = null
                override fun registerReceiveCallback(p0: Executor,p1: TransportHandle.ReceiveCallback) {
                    callbackExecuter = p0
                    callbackFunction = p1
                }

                override fun sendData(p0: ByteArray) {
                    val s = byteToHexString(p0)
                    Log.d("TransportHandle","sending message $s")
                    oobConnector?.sendMessage(constructMessage(DATA_PACKAGE,key, p0))
                }

                override fun close() {
                    TODO("Not yet implemented")
                }

                override fun receiveData(data:ByteArray){
                    if(callbackExecuter!= null)
                        callbackExecuter?.run {callbackFunction?.onReceiveData(data)  }
                }
            }

        var session: RangingSession?=null

        val myRangingSessionCallback = object : RangingSession.Callback {
            override fun onClosed(p0: Int) {
                Log.d("RangingResult", "session $key onClosed: $p0")
                removeSelf()
            }

            @SuppressLint("SetTextI18n")
            override fun onOpenFailed(p0: Int) {
                Log.d("RangingResult", "session $key onOpenFailed: $p0")

                runOnUiThread {
                    exception?.text = "session $key Failed to start ranging. Reason: $p0"
                }
                removeSelf()
            }

            override fun onOpened() {
                Log.d("RangingResult", "session $key onOpened")
                uiMode.startedMeasuring()
            }

            override fun onResults(p0: RangingDevice, p1: RangingData) {
                //Log.d("RangingResult","onResults: $p1")
                val distance = p1.distance?.measurement
                if (distance != null) {
                    gotResult(key, distance)

                    if (p1.rangingTechnology != RangingManager.UWB)
                        oobConnector?.sendMessage(
                            constructMessage(
                                SHARED_RESULT,
                                key,
                                doubleToByteArray(distance)
                            )
                        )
                }
            }

            override fun onStarted(p0: RangingDevice, p1: Int) {
                Log.d("RangingResult", "session $key onStarted $p1")
            }

            override fun onStopped(p0: RangingDevice, p1: Int) {
                Log.d("RangingResult", "session $key onStopped $p1")
                session?.close()
            }
        }
        fun removeSelf(){
            saveRemoveSessions(key)
        }
    }

    private var sessions : MutableMap<Byte,MultiSession> = mutableMapOf()

    fun saveRemoveSessions(key:Byte){
        sessions.remove(key)
        if(sessions.size==0){
            stopMeasuring()
        }
    }


    /**
     * Referenz auf das Context Objekt
     */
    private var rangingManager :RangingManager?  = null

    /**
     *  unterhält/steuert das Ranging
     */
    //private var rangingSession : RangingSession? = null

    /**
     *  gibt Ergebnisse und Status des Rangings zurück
     */

    /**
     * Callback für die Ranging Capabilities
     */
    private var capabilitiesCallback = RangingManager.RangingCapabilitiesCallback { p0 ->
        Log.d("RangingCapa", "Capabilities: $p0")
        availableCapabilities.BLE_CS = p0.csCapabilities != null
        availableCapabilities.UWB = p0.uwbCapabilities != null
        availableCapabilities.WIFI_RTT = p0.rttRangingCapabilities != null
    }

    /**
     * Speichert die letzten Ranging Capabilities
     */
    private val availableCapabilities = MyRangingCapabilities()

    private var gnssProvider: GnssMeasurementProvider? = null

    private val gnssListener = object : GnssMeasurementListener {
        override fun onLocationChanged(location: Location?) {
            Log.d("GnssListener","LocationChanged: $location")
            logEntry(LogEntryType.GnssLocation,"$location")

            oobCallback.statusMessage("LocationChanged:${dateTimeString()}")
        }

        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
            Log.d("GnssListener","Received ${event?.measurements?.size} Measurements")
            logEntry(LogEntryType.GnssMeasurement,event?.measurements?.size.toString())
            for(measurement in event?.measurements!!)
            {
                //Log.d("GnssListener","${measurement}")
            }
        }

        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage?) {
            Log.d("GnssListener","NavigationMessage")
            logEntry(LogEntryType.GnssNavigation,"")
        }

        override fun onGnssStatusChanged(gnssStatus: GnssStatus?) {
            Log.d("GnssListener","StatusChanged: $gnssStatus")
            logEntry(LogEntryType.GnssStatus,"")
        }

        override fun onListenerRegistration(listener: String?, result: Boolean) {
            Log.d("GnssListener","Registration: $listener : $result")
        }

        override fun onNmeaReceived(l: Long, s: String?) {
            //Log.d("GnssListener","Nmea: $l, $s")
            //logEntry(LogEntryType.GnssNema,"")
        }

        override fun onTTFFReceived(l: Long) {
            Log.d("GnssListener","TTFF: $l")
            logEntry(LogEntryType.GnssTTFF,l.toString())
        }

    }


    /**
     * Speichert Ereignisse für die log Datei
     */
    private var logEntries :  ArrayList<String>? = null


    /**
     * steuert die Art der OOB Verbindung, wird durch UI gesetzt
     */
    //private val oobMode = OOBTechnology.BLE

    /**
     * steuert die Art der Ranging Verbindung, wird durch UI oder Partnergerät gesetzt
     */
    private var rangingMode = RangingTechnology.AUTO

    /**
     * Activity Result Handler für die Log Datei
     */
    private val saveLauncher = registerForActivityResult(SaveFileContract()) { uri: Uri? ->
        if (uri != null) writeContentToUri(uri)
        else             onFileSaveCancelled()
    }

    /**
     * Initialisierungen
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        rangingManager = baseContext.getSystemService(RangingManager::class.java) as RangingManager

        initUI()
    }

    /**
     * Initalisierungen für die UI Referenzen und setzen der Button funktionen
     */
    private fun initUI() {
        tvRangeDisplay = findViewById<TextView>(R.id.rangeDisplay)
        swIsController = findViewById<Switch>(R.id.swIsController)
        swMakeLog = findViewById<Switch>(R.id.swMakeLog)
        swUseGNSS = findViewById<Switch>(R.id.swUseGNSS)
        exception = findViewById<TextView>(R.id.exception)
        connectButton = findViewById<Button>(R.id.ConButton)
        connectButton!!.setOnClickListener  { _ -> connect() }
        disconnectButton = findViewById<Button>(R.id.DConButton)
        disconnectButton!!.setOnClickListener  { _ -> disconnect() }
        startMeasuringButton = findViewById<Button>(R.id.StartMsgBtn)
        startMeasuringButton!!.setOnClickListener  { _ -> startMeasuringBtn() }
        stopMeasuringButton = findViewById<Button>(R.id.StopMsgBtn)
        stopMeasuringButton!!.setOnClickListener  { _ -> stopMeasuringBtn() }
        rgTecRANG = findViewById<RadioGroup>(R.id.rgTechnologyRanging)


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
        findViewById<RadioButton>(R.id.rbTecRangUWBRAW).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.UWB_RAW
        }
        findViewById<RadioButton>(R.id.rbTecRangMULTI).setOnCheckedChangeListener { _, isChecked ->
            if(isChecked)rangingMode = RangingTechnology.MULTI
        }

    }

    /**
     * Button funktion für die UI
     */
    private fun stopMeasuringBtn()
    {
        oobConnector?.sendMessage(byteArrayOf(STOP_MEASUREMENT))
        stopMeasuring()
    }

    /**
     * Stoppe die Ranging Verbindung, wird indirekt durch UI oder vom transportHandle aufgerufen
     */
    private fun stopMeasuring(){

        if(sessions.size>0){
            for(s in sessions){
                s.value.session?.stop() ?:sessions.remove(s.key)
            }
        }
        else{
            uiMode.stoppedMeasuring()
            if(swUseGNSS?.isChecked==true)
            {
                gnssProvider?.unregisterAll()
                gnssProvider = null
            }
            if(swMakeLog?.isChecked == true && logEntries!=null)
            {
                safeLog()
            }
        }
    }

    /**
     * Button funktion für die UI oder vom transportHandle(startMeasuringRequest()) aufgerufen
     */
    private fun startMeasuringBtn(){
        if(swIsController?.isChecked==true){
            oobConnector?.sendMessage(byteArrayOf(REQUEST_MEASUREMENT,rangingMode.ordinal.toByte()))
        }
        else{
            startMeasuring()
        }
    }

    /**
     *  Bereitet das Ranging vor. Wählt die Technologie aus und setzt Filter und Einstellungen für nicht-RAW Messungen
     */
    @SuppressLint("NewApi", "MissingPermission", "SetTextI18n")
    private fun startMeasuring(){
        if(rangingMode != RangingTechnology.UWB_RAW) {
            val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
            if ((rangingMode == RangingTechnology.BLE || rangingMode == RangingTechnology.AUTO) && availableCapabilities.BLE_CS) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if ((rangingMode == RangingTechnology.UWB || rangingMode == RangingTechnology.AUTO) && availableCapabilities.UWB) {
                permissions.add(Manifest.permission.RANGING)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
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

            if(sessions.size!=0)
                return

            val session = MultiSession(1)
            sessions[session.key] = session
            session.session = rangingManager?.createRangingSession(myExecutor, session.myRangingSessionCallback)

            var role: Int
            var config: RangingConfig

            val rangingDeviceBuilder = RangingDevice.Builder()
            val rangingDevice = rangingDeviceBuilder.build()

            val deviceHandleBuilder  = DeviceHandle.Builder(rangingDevice, session.handle)
            if(rangingMode == RangingTechnology.BLE){
                val blAdap =  (this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).getAdapter()
                val blDevice = blAdap.getRemoteDevice((oobConnector as BLESuper).getPeerAddress())
                deviceHandleBuilder.setBluetoothDevice(blDevice)
            }
            val deviceHandle = deviceHandleBuilder.build()


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
                val configBuilder = OobInitiatorRangingConfig.Builder().addDeviceHandle(deviceHandle).setRangingTechnologyFilter(filter)
                if(rangingMode == RangingTechnology.UWB){
                    configBuilder.setSecurityLevel(OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE)
                }
                else
                    configBuilder.setSecurityLevel(OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC)
                config = configBuilder.build()

            } else {
                role = RangingPreference.DEVICE_ROLE_RESPONDER
                config = OobResponderRangingConfig.Builder(deviceHandle).build()
            }
            startMeasuring2(config,role,session.session)
        }
        else{
            var peerAddress: ByteArray? = null
            var myAddress: ByteArray? = null
            try{
                //peerAddress = addressStringToByteArray((oobConnector as BLESuper).getPeerAddress()!!)
                //myAddress = addressStringToByteArray((oobConnector as BLESuper).getMyAddress()!!)
                peerAddress = byteArrayOf(0x48,0x30)
                myAddress = byteArrayOf(0x38,0x32)
                if(swIsController?.isChecked == true) {
                    val tmp = myAddress
                    myAddress = peerAddress
                    peerAddress = tmp
                }
                Log.d("RawRanging","Peer Address: $peerAddress; ${byteToHexString(peerAddress!!)}")
                Log.d("RawRanging","My Address: $myAddress; ${byteToHexString(myAddress!!)}")

            }
            catch(_:Exception){
                runOnUiThread{
                    exception?.text = "No BLE device found"
                }
            }

            startRawSessionForAddress(myAddress!!,peerAddress!!)
        }

        if(swIsController?.isChecked == false)//normally false in case of only OOB
        {
            oobConnector?.sendMessage(byteArrayOf(START_MEASUREMENT,rangingMode.ordinal.toByte()))
        }
        if(swUseGNSS?.isChecked == true){
            gnssProvider = GnssMeasurementProvider(this as Activity, this as Context,gnssListener)
        }

        if(swMakeLog?.isChecked == true){
            logEntries = ArrayList<String>()
        }
    }

    /**
     * Bereitet BLE-RAW sessions vor. Abgegrenzt, da möglicherweise erst eine BLE Verbindung aufgebaut bzw. eine Addresse vom Partner angefordert werden muss.
     */
    private fun startRawSessionForAddress(myAddressData:ByteArray, peerAddressData:ByteArray){
        val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.RANGING)
        permissions.add(Manifest.permission.UWB_RANGING)
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)



        if(!askPermissions(this, *permissions.toTypedArray()))
        {
            return
        }
        if(sessions.size!=0)
            return

        val session = MultiSession(1)
        sessions[session.key] = session
        session.session = rangingManager?.createRangingSession(myExecutor, session.myRangingSessionCallback)

        /*val address :String = myAddressData.decodeToString()
        Log.d("RawRanging","Received Address: $address; $myAddressData")*/
        /*if(!BluetoothAdapter.checkBluetoothAddress(address)){
            return
        }*/
        var role: Int
        var config: RangingConfig
        val rangingDevice: RangingDevice = RangingDevice.Builder().build()
        /*val BLECSParams = BleCsRangingParams.Builder(address)
            .setLocationType(BleCsRangingParams.LOCATION_TYPE_INDOOR)
            .setRangingUpdateRate(RawRangingDevice.UPDATE_RATE_NORMAL)
            .setSecurityLevel(BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE)
            .setSightType(BleCsRangingParams.SIGHT_TYPE_LINE_OF_SIGHT)
            .build()*/
        var myAddressData2=myAddressData
        var peerAddressData2=peerAddressData
        if(myAddressData2.size>UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
            myAddressData2 = myAddressData.copyOfRange(0,UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
        if(myAddressData2.size<UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
            myAddressData2 = myAddressData.copyOfRange(0,UwbAddress.SHORT_ADDRESS_BYTE_LENGTH)

        if(peerAddressData2.size>UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
            peerAddressData2 = peerAddressData.copyOfRange(0,UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
        if(peerAddressData2.size<UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)
            peerAddressData2 = peerAddressData.copyOfRange(0,UwbAddress.SHORT_ADDRESS_BYTE_LENGTH)



        val myAddress = UwbAddress.fromBytes(myAddressData2)
        val peerAddress = UwbAddress.fromBytes(peerAddressData2)

        val uwbCC = UwbComplexChannel.Builder().setChannel(UwbComplexChannel.UWB_CHANNEL_9).setPreambleIndex(
            UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11).build()

        val UWBParams = UwbRangingParams.Builder(34,UwbRangingParams.CONFIG_UNICAST_DS_TWR,myAddress,peerAddress)
            .setComplexChannel(uwbCC)
            .setSessionKeyInfo(byteArrayOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16))
            .setRangingUpdateRate(RawRangingDevice.UPDATE_RATE_FREQUENT)
            .setSlotDuration(UwbRangingParams.DURATION_2_MS)
            .build()

        val rawDevice = RawRangingDevice.Builder().setUwbRangingParams(UWBParams).setRangingDevice(rangingDevice).build()

        if (swIsController?.isChecked == true) {
            role = RangingPreference.DEVICE_ROLE_INITIATOR
            config = RawInitiatorRangingConfig.Builder().addRawRangingDevice(rawDevice).build()
            Log.d("RawRanging","I AM INITIATOR")

        } else {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = RawResponderRangingConfig.Builder().setRawRangingDevice(rawDevice).build()
            Log.d("RawRanging","I AM RESPONDER")
        }
        startMeasuring2(config,role,session.session)
    }

    /**
     * startet mit fertigen Configs endgültig das Ranging
     */
    @SuppressLint("SetTextI18n")
    private fun startMeasuring2(config : RangingConfig, role :Int,rangingSession: RangingSession?){
        runOnUiThread {
            tvRangeDisplay?.text = "0,000m"
        }
        val dataConfig = DataNotificationConfig.Builder().setNotificationConfigType(
            DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE).build()
        val rangingSessionConfig : SessionConfig = SessionConfig.Builder().setDataNotificationConfig(dataConfig).build()
        val rangingPreference: RangingPreference =  RangingPreference.Builder(role, config).setSessionConfig(rangingSessionConfig).build()
        rangingSession?.start(rangingPreference)
    }


    /**
     * beendet die OOB Verbindung, wird vom User ausgelöst
     */
    @SuppressLint("SetTextI18n")
    private fun disconnect(){
        if (!askPermissions(this, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT)))
            {exception?.text = "No Permission";return}

        if(oobConnector!=null ) {
            oobConnector?.disconnect()
        }
    }

    /**
     * startet die OOB Verbindung. Wird vom User ausgelöst
     */
    private fun connect() {
        oobConnector = if (swIsController?.isChecked == false) {
            BleServer(this, oobCallback)
        } else {
            BleClient(this, oobCallback, connectButton!!)
        }
    }

    /**
     * Fügt einem Logevent einen Zeitstempel hinzu und speichert es in logEntries
     */
    @OptIn(ExperimentalTime::class)
    public fun logEntry(type:LogEntryType,msg:String){
        if(logEntries==null||swMakeLog?.isChecked==false)
            return
        var s = "[${dateTimeString()}],${type.name},"
        s+= msg
        s+= "\n"
        logEntries?.add(s)
    }

    /**
     * Startet den Log Datei Dialog, wird von StopMeasuring() aufgerufen
     */
    public fun safeLog(){
        if(logEntries==null || logEntries?.size==0) {
            onFileSaveCancelled()
            return
        }
        saveLauncher.launch(
            SaveFileInput(
                suggestedFileName = "rangingLog${dateTimeString()}.txt",
                mimeType          = MimeTypes.TEXT_VTT
            )
        )
    }

    /**
     * Verarbeitet die vom MyRangingSessionCallback empfangenen Daten  (UI und log)
     */
    @SuppressLint("DefaultLocale")
    public fun gotResult(key : Byte, data : Double){
        runOnUiThread {
            tvRangeDisplay?.text = buildString {
                append(String.format("%.3f", data))
                append("m")
            }
        }
        if(swMakeLog?.isChecked==true ){
            var s = byteToHexString(key)
            s+=data.toString()
            logEntry(LogEntryType.DistMeasurement,s)
        }
    }

    /**
     * Wird genutzt, um die zuletzt empfangenen RangingCapabilities zu speichern
     */
    public inner class MyRangingCapabilities{
        var BLE_CS:Boolean = false
        //var BLE_RSSI:Boolean = false
        var UWB:Boolean = false
        var WIFI_RTT:Boolean = false
        //var WIFI_PD :Boolean = false
    }

    public interface OobConnection{
        /**
         * Gibt Daten an anderes Gerät weiter. Ruft messageReceived() beim Callback der Gegenseite auf
         */
        abstract fun sendMessage(data: ByteArray)

        /**
         * verrät die Rolle des Verbinders
         */
        abstract fun isInitiator():Boolean

        /**
         * trennt die Verbindung
         */
        abstract fun disconnect()

        /**
         *  Kann von OOB implementierungen genutzt werden um Speicher oder Ports frei zu geben
         */
        fun destroy(){}

    }

    public interface OobConnectionCallback{
        /**
         * Verbindung wurde erfolgreich aufgebaut, es kann kommuniziert werden
         */
        abstract fun connectionEstablished()

        /**
         * Verbindung wurde geschlossen, es kann nicht kommuniziert werden
         */
        abstract fun connectionClosed()

        /**
         * Von der Gegenseite wurden Daten empfangen
         */
        abstract fun messageReceived(data:ByteArray)

        /**
         * Eine Statusmeldung von der OOBConnection wird übergeben
         */
        abstract fun statusMessage(message:String)
    }

    public interface HandleCallback :TransportHandle{
        abstract fun receiveData(data:ByteArray)
    }


    /**
     * Generiert einen String mit aktuellem Datum und Zeit
     */
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
            if (resultCode == RESULT_OK) intent?.data else null
    }


    fun onFileSaveCancelled() {
        runOnUiThread {
            Toast.makeText(this, "Save cancelled.", Toast.LENGTH_SHORT).show()
        }
        logEntries = null
    }


    /**
     * Datei wird unter angegebner URI gespeichert
     */
    private fun writeContentToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
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

    private enum class UiMode{
        Idle, Connecting,Connected,Measuring
    }

    /**
     * wird genutzt, um die aktuell ausgewählte OOB Technologie zu speichern
     */
    enum class OOBTechnology{
        BLE,WIFIDirect,WIFIAware
    }

    /**
     * wird genutzt, um die aktuell ausgewählte Ranging Technologie zu speichern und zu kommunizieren
     */
    enum class RangingTechnology{
        AUTO,WIFI,BLE,UWB_RAW,UWB,MULTI    //Reihenfolge muss der der UI entsprechen
    }

    enum class LogEntryType{
        DistMeasurement,GnssLocation,GnssMeasurement,GnssNavigation,GnssStatus,GnssNmea,GnssTTFF
    }

    /**
     * passt die UI und lokale Variable an, wenn vom anderen Gerät eine andere Ranging Technologie verlangt wird
     */
    private fun setRangingTechnology(mode: RangingTechnology){
        rangingMode = mode
        runOnUiThread{
            rgTecRANG?.check(rgTecRANG!!.getChildAt(mode.ordinal).id)
        }
    }

    companion object {
        /**
         * Einheitliche Methode um Berechtigungen zu prüfen und ggf. zu erfragen
         */
        fun askPermissions(context: Context, vararg permissions: String): Boolean {
            val activity = context as? Activity

            val missingPermissions = mutableListOf<String>()
            Log.d("Permissions","Checking permissions: ${permissions.joinToString()}")
            if(permissions.isEmpty())return true
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
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
            var allGranted = true
            for (permission in missingPermissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false

                }
            }

            return allGranted
        }

        /**
         * Setzt alle RadioButtons einer RadioGroup auf (nicht) klickbar
         */
        fun toggleRadioGroup(rg:RadioGroup,enabled:Boolean){
            for (i in 0 until rg.size){
                rg.getChildAt(i).isEnabled = enabled
            }
        }

        /**
         * Verwandelt ein ByteArray in ein String von Hexadezimalzahlen
         */
        fun byteToHexString(data:ByteArray):String{
            var s = ""
            for(b in data)
            {
                s+= byteToHexString(b)
            }
            return s
        }
        fun byteToHexString(data:Byte):String{
            var s = ""
                s+= bit4ToHex((data.toInt() shr 4).toByte())
                s+= bit4ToHex(data)
                s+=';'
            return s
        }

        fun addressStringToByteArray(s:String):ByteArray{
            s.uppercase(getDefault())
            val bytes: MutableList<Byte> = mutableListOf()
            var byte :Byte =0
            for(c in s.toCharArray()){
                when(c){
                    ':'-> {bytes+=byte;byte=0}
                    in '0'..'9' -> byte = ((byte.toInt() shl 4).toByte() + (c.code - '0'.code)).toByte()
                    in 'A'..'F' -> byte = ((byte.toInt() shl 4).toByte() + (c.code - 'A'.code + 10)).toByte()
                }
            }
            bytes+=byte
            return bytes.toByteArray()
        }

        /**
         * Wandelt die ersten 4 Bits eines Bytes in das entsprechende Hexadezimalzeichen um
         */
        fun bit4ToHex(data:Byte):Char{
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

        /**
         * Wandelt ein Double in ein ByteArray um
         */
        fun doubleToByteArray(value: Double): ByteArray {
            val bits = java.lang.Double.doubleToLongBits(value)
            return ByteArray(8) { i -> (bits shr (56 - i * 8)).toByte() }
        }
        /**
         * Wandelt ein ByteArray der länge 8 in ein Double um
         */
        fun byteArrayToDouble(bytes: ByteArray): Double {
            require(bytes.size == 8)
            val bits = bytes.foldIndexed(0L) { i, acc, b ->
                acc or ((b.toLong() and 0xFF) shl (56 - i * 8))
            }
            return java.lang.Double.longBitsToDouble(bits)
        }

        fun constructMessage(mode:Byte,key:Byte, data: ByteArray):ByteArray{
            var sendData = byteArrayOf(mode,key)
            sendData += data
            return sendData
        }
    }


}



