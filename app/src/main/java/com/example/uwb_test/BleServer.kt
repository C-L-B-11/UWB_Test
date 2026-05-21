package com.example.uwb_test

import android.Manifest
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


class BleServer(private val context : Context,private val callback : MainActivity.OobConnectionCallback): MainActivity.OobConnection {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var myDevice: BluetoothDevice? = null
    private var sendMessageBuffer : ByteArray? = null
    private var recvMessageBuffer : ByteArray? = null

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
            val received = value.toString(Charsets.UTF_8)
            Log.d("BLE_SERVER", "recvFragment: ${MainActivity.byteToHexString(value)}")

            val mode:Byte = value[0]
            var data = value.copyOfRange(1,value.size)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
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

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun commsMessage(mode:Byte,data:ByteArray){
            if(recvMessageBuffer==null){
                recvMessageBuffer = ByteArray(0)
            }
            recvMessageBuffer = recvMessageBuffer!! + data

            if(mode==SNIPPET_LAST){
                callback.messageReceived(recvMessageBuffer!!)
                recvMessageBuffer=null
            }


            if(mode==SNIPPET_INTERMEDIATE){
                sendCodedMessage(SNIPPET_RECIEVED,ByteArray(0))
                return
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
        if ((ActivityCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
        {
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN),1)
            if ((ActivityCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
            {Log.d("BleServer","No Permission")
                flag=false}
        }
        if(flag) {
            val advertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true)
                .setDiscoverable(true).setTimeout(10000).build()
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

    /*@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage( message: String) {

        val value = message.toByteArray()
        sendMessage(value)
    }*/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data:ByteArray) {
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
        if(myDevice!=null){
            val d:BluetoothDevice = myDevice!!
            gattServer?.notifyCharacteristicChanged(d, characteristic, false, sendData)
            Log.d("BLE_SERVER","sendFragment: ${MainActivity.byteToHexString(sendData)}")
        }
    }

}