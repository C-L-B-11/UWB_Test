package com.example.uwb_test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.uwb_test.MainActivity.Companion.askPermissions


@SuppressLint("MissingPermission")
class BleServer(private val context : Context, private val callback : MainActivity.OobConnectionCallback): MainActivity.OobConnection {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var myDevice: BluetoothDevice? = null

    private val sP : (ByteArray) ->Unit = {d:ByteArray -> sendFinalMessage(d)}
    private val rM : (ByteArray) ->Unit = {
        d:ByteArray -> callback.messageReceived(d)
        //Log.d("BLE_SERVER","message Received: ${MainActivity.byteToHexString(d)}")
    }
    private var tcpAdapter = TPCforBLE(sP,rM,20)

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
                callback.connectionEstablished()
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
            //Log.d("BLE_SERVER", "recvFragment: ${MainActivity.byteToHexString(value)}")

            val mode:Byte = value[0]
            var data = value.copyOfRange(1,value.size)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            when(mode){
                START_MEASUREMENT -> callback.startMeasuringOrder()
                REQUEST_MEASUREMENT -> callback.requestMeasuring()
                STOP_MEASUREMENT -> callback.stopMeasuring()
                SHARED_RESULT -> callback.sharedResult(MainActivity.byteArrayToDouble(data))
                else -> if(!tcpAdapter.receivedPackage(value))Log.d("BLE_SERVER","tcpAdapter failed to process")
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

    private class MyBluetoothAdvertiseCallback: AdvertiseCallback(){
        override fun onStartFailure(errorCode: Int) {
            Log.d("BluetoothAdvertiseCallback","onStartFailure: errorCode: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BluetoothAdvertiseCallback","onStartSuccess: settingsInEffect: $settingsInEffect")
        }
    }
    private var gattServer : BluetoothGattServer? = null


    init{
        var flag=true
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT)))
        {
            Log.d("BleServer","No Permission")
            flag = false
        }
        if(flag) {
            val advertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true)
                .setDiscoverable(true).setTimeout(30000).build()
            val advertiseData: AdvertiseData =
                AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true)
                    .build()
            val advertiseCallback = MyBluetoothAdvertiseCallback()
            bluetoothManager.adapter?.bluetoothLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseData,
                advertiseCallback
            )




            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
        }


    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect(){
        tcpAdapter.reset()
        if(bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).size==0){
            gattServer?.close()
            callback.connectionClosed()

        }
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
    override fun sharedResult(distance: Double) {
        sendCodedMessage(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun stopMeasuring() {
        sendCodedMessage(STOP_MEASUREMENT,ByteArray(0))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data:ByteArray) {
        //Log.d("BLE_SERVER","send message: ${MainActivity.byteToHexString(data)}")
        if(!tcpAdapter.sendMessage(data)){
            Log.d("BLE_Server","Sending through tcp failed")
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
        if(myDevice!=null){
            val d:BluetoothDevice = myDevice!!
            gattServer?.notifyCharacteristicChanged(d, characteristic, false, data)
            //Log.d("BLE_SERVER","sendFragment: ${MainActivity.byteToHexString(data)}")
        }
    }

}