package com.example.uwb_test

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission


class BleClient(private val context: Context, rF:(ByteArray)->Unit) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val retFunc: (ByteArray) -> Unit = rF

    private var sendMessageBuffer : ByteArray? = null
    private var recvMessageBuffer : ByteArray? = null

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
            val message = value.toString(Charsets.UTF_8)
            //Log.d("BLE_CLIENT", "Notify:$message")

            val mode:Byte = value[0]
            if(mode==SNIPPET_RECIEVED){
                startSend()
                return
            }
            var data = value.copyOfRange(1,value.size)

            if(recvMessageBuffer==null){
                recvMessageBuffer = ByteArray(0)
            }
            recvMessageBuffer = recvMessageBuffer!! + data

            if(mode==SNIPPET_LAST){
                data = recvMessageBuffer!!
                recvMessageBuffer=null
                retFunc(data)
            }

            if(mode==SNIPPET_INTERMEDIATE){
                sendCodedMessage(SNIPPET_RECIEVED,ByteArray(0))
                return
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        val value = message.toByteArray()
        sendMessage(value)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(data: ByteArray) {
        if(sendMessageBuffer!=null){
            Log.d("BLE_SERVER","Still busy with sending")
        }
        sendMessageBuffer = data
        startSend()
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


        bluetoothGatt?.writeCharacteristic(characteristic, sendData, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
}