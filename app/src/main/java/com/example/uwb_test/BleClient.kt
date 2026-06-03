package com.example.uwb_test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.uwb_test.MainActivity.Companion.askPermissions
import java.util.Collections
import java.util.Dictionary


@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val callback: MainActivity.OobConnectionCallback):MainActivity.OobConnection {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothGatt: BluetoothGatt? = null

    private var sendMessageBuffer : ByteArray? = null
    private var recvMessageBuffer : ByteArray? = null

    private var bleScanner : BluetoothLeScanner? = null
    private var bleScanCallback : MyBluetoothScannerCallback? = null

    private val devices : MutableMap<String, BluetoothDevice> = mutableMapOf<String, BluetoothDevice>()

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE_CLIENT","Disconnected")
                callback.connectionClosed()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service.getCharacteristic(CHAR_UUID)
            Log.d("BLE_CLIENT","discovered Services")
            // Enable notifications
            gatt.setCharacteristicNotification(characteristic, true)
            callback.connectionEstablished()
            // Read initial value
            //gatt.readCharacteristic(characteristic)
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
            //val message = value.toString(Charsets.UTF_8)
            Log.d("BLE_CLIENT", "recvFragment:${MainActivity.byteToHexString(value)}")

            val mode:Byte = value[0]
            var data = value.copyOfRange(1,value.size)
            when(mode){
                SNIPPET_RECIEVED -> startSend()
                START_MEASUREMENT -> callback.startMeasuringOrder()
                REQUEST_MEASUREMENT -> callback.requestMeasuring()
                STOP_MEASUREMENT -> callback.stopMeasuring()
                SNIPPET_LAST -> commsMessage(mode,data)
                SNIPPET_INTERMEDIATE -> commsMessage(mode,data)
                SHARED_RESULT -> callback.sharedResult(MainActivity.byteArrayToDouble(data))
            }
        }
    }

    init{
        var flag=true
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE)))
        {
            Log.d("BleClient","No Permission")
            flag=false
        }
        if(flag){
            bleScanner = bluetoothManager.adapter.bluetoothLeScanner
            if(bleScanner!=null){
                bleScanCallback = MyBluetoothScannerCallback()
                Log.d("BleClient","Start Scanning")


                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .build()

                val scanFilter = ScanFilter.Builder().build()
                bleScanner?.startScan(Collections.singletonList(scanFilter), scanSettings,bleScanCallback!!)

                /*
                val list: List<BluetoothDevice> = bluetoothManager.adapter.bondedDevices.toList()
                val i =list.size
                //exception?.text = "found $i devices"

                if(i!=0) {
                    val bDev : BluetoothDevice = list[0]
                    if(bDev !=null)
                        connect(bDev)
                    else
                        Log.d("BleClient","Null Device")
                }
                else
                    Log.d("BleClient","No Devices")*/
            }
            else{
                Log.d("BleClient","No Scanner")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun commsMessage(mode:Byte,data:ByteArray){
        if(recvMessageBuffer==null){
            recvMessageBuffer = ByteArray(0)
        }
        recvMessageBuffer = recvMessageBuffer!! + data

        if(mode==SNIPPET_LAST){
            callback.messageReceived(recvMessageBuffer!!)
            recvMessageBuffer = null
        }

        if(mode==SNIPPET_INTERMEDIATE){
            sendCodedMessage(SNIPPET_RECIEVED,ByteArray(0))
            return
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)))
        {
            Log.d("BleClient","No Permission")
            return
        }
        if(bleScanner!= null)
            bleScanner?.stopScan(bleScanCallback!!)

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }


    @SuppressLint("MissingPermission")
    override fun disconnect() {
        if (bleScanner != null) {
            if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)))
            {
                Log.d("BleClient","No Permission")
                return
            }
            bleScanner?.stopScan(bleScanCallback)
        }
        if (bluetoothGatt != null) {
            bluetoothGatt?.close()
        }
        callback.connectionClosed()

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun startMeasuring() {
        sendCodedMessage(START_MEASUREMENT,ByteArray(0))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun requestMeasuring() {
        sendCodedMessage(REQUEST_MEASUREMENT,ByteArray(0))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun stopMeasuring() {
        sendCodedMessage(STOP_MEASUREMENT,ByteArray(0))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sharedResult(distance: Double) {
        sendCodedMessage(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }


    public inner class MyBluetoothScannerCallback: ScanCallback(){
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onBatchScanResults(results : List<ScanResult> )
        {
            val i = results.size
            Log.d("ScanCallback","onBatchScanResults: $i devices")

            //connect(results[0].device)
        }

        override fun onScanFailed(errorCode : Int)
        {
            Log.d("ScanCallback","onScanFailed: $errorCode")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType : Int, result : ScanResult )
        {
            Log.d("ScanCallback","onScanResult: $callbackType, ${result.device.getName()}")
            //TODO gerät auswählen
            if(!devices.contains(result.device.address))
            {
                devices[result.device.address] = result.device

            }
            //if(bluetoothGatt==null)
             //   connect(result.device)
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data: ByteArray) {
        if(sendMessageBuffer!=null){
            Log.d("BLE_SERVER","Still busy with sending")
        }
        sendMessageBuffer = data
        startSend()
    }

    override fun isInitiator(): Boolean {
        return false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startSend(){
        if(sendMessageBuffer==null){
            Log.d("BLE_SERVER","NothingToSend")
            return
        }
        val sendSize:Int = sendMessageBuffer?.size!!
        var data:ByteArray?
        var mode:Byte
        if(sendSize>19) {
            data = sendMessageBuffer?.copyOfRange(0, 19)
            sendMessageBuffer = sendMessageBuffer?.copyOfRange(19,sendSize)
            mode = SNIPPET_INTERMEDIATE
        }
        else{
            data = sendMessageBuffer
            mode = SNIPPET_LAST
            sendMessageBuffer = null
        }

        sendCodedMessage(mode,data!!)
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCodedMessage(mode:Byte, data:ByteArray){
        if(data.size>19){
            throw Exception("Data too long")

        }
        var sendData = ByteArray(1)
        sendData[0]=mode
        sendData += data

        val service = bluetoothGatt?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHAR_UUID)
        Log.d("BLE_CLIENT","sendFragment: ${MainActivity.byteToHexString(sendData)}")

        bluetoothGatt?.writeCharacteristic(characteristic, sendData, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
}