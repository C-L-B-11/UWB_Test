package com.example.uwb_test

import android.Manifest
import android.annotation.SuppressLint

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
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.uwb_test.MainActivity.Companion.askPermissions
import com.example.uwb_test.MainActivity.RangingTechnology


@SuppressLint("MissingPermission")
class BleServer(private val context : Context, private val callback : MainActivity.OobConnectionCallback): BLESuper() {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /**
     * Verbundenes Gerät
     */
    private var myDevice: BluetoothDevice? = null

    /**
     * Callback Funktion für den tcpAdapter zum Senden von Daten
     */
    private val sendPayloadFunc : (ByteArray) ->Unit = { d:ByteArray -> sendFinalMessage(d)}

    /**
     * Callback Funktion fur den tcpAdapter zum Melden einer vollständig empfangenen Nachricht
     */
    private val receivedMessageFunc : (ByteArray) ->Unit = {d:ByteArray -> callback.messageReceived(d)}

    /**
     * tcpAdapter zerstückelt Nachrichten auf eine Maximallänge von 20 Bits und überwacht die vollständige Übertragung in beide Richtungen
     */
    private var tcpAdapter = TPCforBLE(sendPayloadFunc,receivedMessageFunc,20)

    /**
     * Bluetooth Gatt Characteristic die zur Kommunikation verwendet wird. Sowohl Server als auch Client können einen Wert (Data) setzten, und werden benachrichtigt, wenn der andere es tut.
     */
    public val characteristic = BluetoothGattCharacteristic(
        CHAR_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_WRITE or
                BluetoothGattCharacteristic.PERMISSION_READ
    )


    /**
     *  Callback für alles, was der gatt Server zu berichten hat
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if(newState == BluetoothProfile.STATE_CONNECTED){
                myDevice = device
                Log.d("BLE_SERVER","Connected: $device")
                callback.connectionEstablished()
            }
            if(newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                disconnect()
            }

        }


        /**
         * Hier treffen alle Nachrichten vom anderen Gerät ein
         */
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
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val mode = value[0]
            if(mode==SHARED_RESULT){
                callback.messageReceived(value)
            }
            else
                if(!tcpAdapter.receivedPackage(value)) {
                    Log.d("BLE_SERVER", "tcpAdapter failed to process")
                    callback.statusMessage("Communication error")
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

    /**
     * Callback für das BluetoothAdvertising, nicht weiter relevant
     */
    private val advertiseCallback = object : AdvertiseCallback(){
        override fun onStartFailure(errorCode: Int) {
            Log.d("BluetoothAdvertiseCallback","onStartFailure: errorCode: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BluetoothAdvertiseCallback","onStartSuccess: settingsInEffect: $settingsInEffect")
        }
    }

    /**
     * GattServer der vom BLEServer gehosted wird
     */
    private var gattServer : BluetoothGattServer? = null


    init{
        var flag=true
        if (!askPermissions(context, *arrayOf(Manifest.permission.BLUETOOTH_CONNECT)))
        {
            Log.d("BleServer","No Permission")
            callback.statusMessage("No Permission")
            flag = false
        }
        if(flag) {
            val advertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(true)
                .setDiscoverable(true).setTimeout(30000).build()
            val advertiseData: AdvertiseData =
                AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true)
                    .build()

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
        else
        {
            callback.statusMessage("Devices still connected")
            gattServer?.cancelConnection(myDevice!!)
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendMessage(data:ByteArray) {
        //Log.d("BLE_SERVER","send message: ${MainActivity.byteToHexString(data)}")
        val mode = data[0]
        if(mode==SHARED_RESULT){
            sendFinalMessage(data)
        }
        else
        if(!tcpAdapter.sendMessage(data)){
            Log.d("BLE_Server","Sending through tcp failed")
            callback.statusMessage("Communication error")
        }
    }

    override fun isInitiator(): Boolean {
        return false
    }


    /**
     * Kombiniert den Nachrichten Typ mit der nutzlast und sendet es an den BLEClient
     */
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

    /**
     * Sendet die Daten an den BLEClient
     */
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

    /**
     * Liefert die MAC Adresse des anderen Bluetooth Gerätes zurück
     */
    override fun getAddress() :String?{
        if(myDevice!=null){
            return myDevice?.address
        }
        return null
    }

}