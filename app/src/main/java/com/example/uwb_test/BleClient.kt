package com.example.uwb_test

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import androidx.annotation.RequiresPermission
import com.example.uwb_test.MainActivity.Companion.askPermissions
import com.example.uwb_test.MainActivity.RangingTechnology
import java.util.Collections


@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val oobCallback: MainActivity.OobConnectionCallback, private val view: View):BLESuper {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /**
     * GattServer der vom BLEServer gehosted wird
     */
    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * Callback für Änderungen auf dem Gatt Server
     */
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE_CLIENT","onConnectionStateChange: $status, $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE_CLIENT","Disconnected")
                oobCallback.connectionClosed()
            }
        }

        override fun onServiceChanged(gatt:BluetoothGatt){
            Log.d("BLE_CLIENT","onServiceChanged:${gatt.device.address}")

        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service.getCharacteristic(CHAR_UUID)
            Log.d("BLE_CLIENT","discovered Services")
            // Enable notifications
            gatt.setCharacteristicNotification(characteristic, true)
            oobCallback.connectionEstablished()
            // Read initial value
            //gatt.readCharacteristic(characteristic)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            //Log.d("BLE_CLIENT", "Read: $value")

        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value:ByteArray
        ) {
            //Log.d("BLE_CLIENT", "recvFragment:${MainActivity.byteToHexString(value)}")

            val mode:Byte = value[0]
            var data = value.copyOfRange(1,value.size)
            when(mode){
                START_MEASUREMENT -> oobCallback.startMeasuringOrder(RangingTechnology.entries[data[0].toInt()])
                REQUEST_MEASUREMENT -> oobCallback.requestMeasuring(RangingTechnology.entries[data[0].toInt()])
                STOP_MEASUREMENT -> oobCallback.stopMeasuring()
                SHARED_RESULT -> oobCallback.sharedResult(MainActivity.byteArrayToDouble(data))
                else -> if(!tcpAdapter.receivedPackage(value)){
                    oobCallback.statusMessage("Communication error")
                }
            }
        }
    }

    /**
     * Callback Funktion für den tcpAdapter zum Senden von Daten
     */
    private val sendPayloadFunc : (ByteArray) ->Unit = { d:ByteArray -> sendFinalMessage(d)}

    /**
     * Callback Funktion fur den tcpAdapter zum Melden einer vollständig empfangenen Nachricht
     */
    private val receivedMessageFunc : (ByteArray) ->Unit = { d:ByteArray -> oobCallback.messageReceived(d)}

    /**
     * tcpAdapter zerstückelt Nachrichten auf eine Maximallänge von 20 Bits und überwacht die vollständige Übertragung in beide Richtungen
     */
    private var tcpAdapter = TPCforBLE(sendPayloadFunc,receivedMessageFunc,20)


    /**
     * Scanner zum finden von BLE Advertisern
     */
    private var bleScanner : BluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner

    /**
     * Callback für den Scanner zum Melden von gefundenen BLE Advertisern
     */
    private val bleScanCallback = object : ScanCallback(){
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onBatchScanResults(results : List<ScanResult> )
        {
            val i = results.size
            Log.d("ScanCallback","onBatchScanResults: $i devices")
        }

        override fun onScanFailed(errorCode : Int)
        {
            Log.d("ScanCallback","onScanFailed: $errorCode")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType : Int, result : ScanResult )
        {
            //Log.d("ScanCallback","onScanResult: $callbackType, ${result.device.name}")
            if(devices.none { it?.address == result.device.address })
            {
                Log.d("ScanCallback","Found new device: ${result.device.address}")
                if(popupMenu==null){        //generiere das popup Menu wenn es noch nicht existiert
                    popupMenu = PopupMenu(context,view)


                    popupMenu?.setOnMenuItemClickListener { item ->
                        val device = devices[item.itemId]
                        popupMenu?.dismiss()
                        if(device!=null)
                            connect(device)

                        true
                    }
                }

                devices.add(result.device)
                val index : Int = devices.size-1
                val name : CharSequence = result.device.name ?: "Unknown"
                popupMenu?.menu?.add(0, index,0,name)
                popupMenu?.show()
            }
        }
    }

    /**
     * Speichert die einzigartigen Geräte, die vom bleScanCallback gefunden wurden, um sie im PopupMenu anzuzeigen und sich mit ihnen zu verbinden
     */
    private val devices : MutableList<BluetoothDevice?> = mutableListOf<BluetoothDevice?>()

    /**
     * Popup Liste aus der ein gefundenes Gerät zum Verbinden gewählt werden kann
     */
    private var popupMenu : PopupMenu? = null


    init{
        var flag=true
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE)))
        {
            Log.d("BleClient","No Permission")
            flag=false
        }
        if(flag){
            bleScanner = bluetoothManager.adapter.bluetoothLeScanner

            Log.d("BleClient","Start Scanning")


            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()

            val scanFilter = ScanFilter.Builder().build()
            bleScanner.startScan(Collections.singletonList(scanFilter), scanSettings,bleScanCallback)

        }
    }

    /**
     * Gerät das aus dem Popup Menu gewählt wurde, wird Verbunden
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)))
        {
            Log.d("BleClient","No Permission")
            return
        }

        bleScanner.stopScan(bleScanCallback)

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /**
     * beendet die Verbindung zum Bluetooth Gerät, Klasse wird geschlossen
     */
    @SuppressLint("MissingPermission")
    override fun disconnect() {
        tcpAdapter.reset()

        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)))
        {
            Log.d("BleClient","No Permission")
            return
        }
        bleScanner.stopScan(bleScanCallback)

        if (bluetoothGatt != null) {
            bluetoothGatt?.close()
        }
        oobCallback.connectionClosed()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun startMeasuring(mode:RangingTechnology) {
        sendCodedMessage(START_MEASUREMENT,byteArrayOf(mode.ordinal.toByte()))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun requestMeasuring(mode:RangingTechnology) {
        sendCodedMessage(REQUEST_MEASUREMENT,byteArrayOf(mode.ordinal.toByte()))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun stopMeasuring() {
        sendCodedMessage(STOP_MEASUREMENT,ByteArray(0))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun shareResult(distance: Double) {
        sendCodedMessage(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }




    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data: ByteArray) {
        //Log.d("BLE_CLIENT","send message: ${MainActivity.byteToHexString(data)}")
        if(!tcpAdapter.sendMessage(data)){
            Log.d("BLE_CLIENT","Sending through tcp failed")
            oobCallback.statusMessage("Communication error")
        }
    }

    override fun isInitiator(): Boolean {
        return false
    }



    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCodedMessage(mode:Byte, data:ByteArray){
        if(data.size>19){
            throw Exception("Data too long")
        }
        var sendData = ByteArray(1)
        sendData[0]=mode
        sendData += data
        sendFinalMessage(sendData)
    }

    private fun sendFinalMessage(data:ByteArray){
        if(data.size>20){
            throw Exception("Data too long")
        }
        val service = bluetoothGatt?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHAR_UUID)
        //Log.d("BLE_CLIENT","sendFragment: ${MainActivity.byteToHexString(data)}")

        bluetoothGatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }

    override fun getAddress(): String? {
        if(bluetoothGatt!=null){
            return bluetoothGatt?.device?.address
        }
        return null

    }
}