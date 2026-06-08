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
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.uwb_test.MainActivity.Companion.askPermissions
import java.util.Collections
import java.util.Dictionary


@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val callback: MainActivity.OobConnectionCallback, private val view: View):MainActivity.OobConnection {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothGatt: BluetoothGatt? = null

    private val sP : (ByteArray) ->Unit = {d:ByteArray -> sendFinalMessage(d)}
    private val rM : (ByteArray) ->Unit = {d:ByteArray -> callback.messageReceived(d)}
    private var tcpAdapter = TPCforBLE(sP,rM,20)
    private var bleScanner : BluetoothLeScanner? = null
    private var bleScanCallback : MyBluetoothScannerCallback? = null

    private val devices : MutableMap<String, Int> = mutableMapOf<String,Int>()
    private val devices2 : MutableList<BluetoothDevice?> = mutableListOf<BluetoothDevice?>()
    private var popupMenu : PopupMenu? = null

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
                START_MEASUREMENT -> callback.startMeasuringOrder()
                REQUEST_MEASUREMENT -> callback.requestMeasuring()
                STOP_MEASUREMENT -> callback.stopMeasuring()
                SHARED_RESULT -> callback.sharedResult(MainActivity.byteArrayToDouble(data))
                else -> tcpAdapter.receivedPackage(value)
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
                popupMenu = PopupMenu(context,view)


                popupMenu?.setOnMenuItemClickListener { item ->
                    val device = devices2[item.itemId]
                    popupMenu?.dismiss()
                    if(device!=null)
                        connect(device)

                    true
                }*/

                /*devices2.add(null)
                val index : Int = devices2.size-1
                devices["0"] = index
                val name : CharSequence = "TestGerät,DON'T TOUCH"
                popupMenu?.menu?.add(0, index,0,name)*/
                //popupMenu?.show()
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
        tcpAdapter.reset()

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
            Log.d("ScanCallback","onScanResult: $callbackType, ${result.device.name}")

            if(!devices.contains(result.device.address))
            {
                if(popupMenu==null){
                    popupMenu = PopupMenu(context,view)


                    popupMenu?.setOnMenuItemClickListener { item ->
                        val device = devices2[item.itemId]
                        popupMenu?.dismiss()
                        if(device!=null)
                            connect(device)

                        true
                    }
                }


                devices2.add(result.device)
                val index : Int = devices2.size-1
                devices[result.device.address] = index
                val name : CharSequence = if (result.device.name==null)  "Unknown" else result.device.name
                popupMenu?.menu?.add(0, index,0,name)
                popupMenu?.show()
            }
            //if(bluetoothGatt==null)
             //   connect(result.device)
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data: ByteArray) {
        if(!tcpAdapter.sendMessage(data)){
            Log.d("BLE_SERVER","Sending through tcp failed")
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
        Log.d("BLE_CLIENT","sendFragment: ${MainActivity.byteToHexString(data)}")

        bluetoothGatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
}