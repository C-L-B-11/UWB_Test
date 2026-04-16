package com.example.uwb_test


import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.ble.rssi.BleRssiRangingParams
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import android.ranging.oob.TransportHandle
import android.ranging.raw.RawRangingDevice
import android.ranging.raw.RawResponderRangingConfig
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import java.util.concurrent.Executor


class MainActivity  : AppCompatActivity() {

    private var exception: TextView? = null

    private var etSessionId: EditText? = null
    private var etSessionKeyInfo: EditText? = null
    private var etSubSessionKeyInfo: EditText? = null
    private var etPartnerAddress: EditText? = null
    private var tvRangeDisplay: TextView?=null
    private var swIsController: Switch? = null
    private var start: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager : RangingManager? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        rangingManager = baseContext.getSystemService("RangingManager") as RangingManager;
        InitUI()
        //uwbMan = UwbManager.createInstance(baseContext)

    }

    private fun InitUI() {

        etSessionId = findViewById<EditText>(R.id.etSessionId)
        etSessionKeyInfo = findViewById<EditText>(R.id.etSessionKeyInfo)
        etSubSessionKeyInfo = findViewById<EditText>(R.id.etSubSessionKeyInfo)
        etPartnerAddress = findViewById<EditText>(R.id.etPartnerAddress)
        tvRangeDisplay = findViewById<TextView>(R.id.rangeDisplay)
        swIsController = findViewById<Switch>(R.id.swIsController)
        exception = findViewById<TextView>(R.id.exception)
        start = findViewById<Button>(R.id.ConButton)
        start!!.setOnClickListener  { v: View? -> connect() }

    }



    @RequiresPermission(Manifest.permission.RANGING)
    private fun connect() {
        val myCallback = myCallback()
        val myExecuter = myExecutor()
        var mySession = rangingManager?.createRangingSession(myExecuter, myCallback)
        var role=0
        var config : RangingConfig
        if(swIsController?.isChecked == true) {
            role=RangingPreference.DEVICE_ROLE_INITIATOR
            config = OobInitiatorRangingConfig.Builder().build()
        }
        else
        {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = OobResponderRangingConfig.Builder(
                DeviceHandle.Builder(
                    RangingDevice.Builder().build(),
                TransportHandle.
                ).build()
            ).build()
        }

        var rangingPreference: RangingPreference.Builder =  RangingPreference.Builder(role, config)

        //val myRangingPreference = RangingPreference.Builder();
        mySession?.start(rangingPreference.build())



        /*
        if (!getPackageManager().hasSystemFeature("android.hardware.uwb")) {
            exception?.setText("Uwb feature not available")
            return
        }*/
        /*
        val uwbManager = UwbManager.createInstance(baseContext)
        scope.launch {
            val sessionScope: UwbClientSessionScope = uwbManager.clientSessionScope()
            val ucc: UwbComplexChannel = UwbComplexChannel(
                channel = 1,
                preambleIndex = 1
            )
            val parameters = RangingParameters(

                sessionId = ParseStringToInt(etSessionId?.text.toString()),
                subSessionId = 0,
                sessionKeyInfo = ParseStringToByteArry(etSessionKeyInfo?.text.toString()), // shared key
                subSessionKeyInfo = ParseStringToByteArry(etSubSessionKeyInfo?.text.toString()), // shared key,
                complexChannel = ucc,
                peerDevices = listOf(
                    UwbDevice.createForAddress(ParseStringToByteArry(etPartnerAddress?.text.toString()))
                ),
                updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
            )

            if(swIsController?.isChecked == true)
            {
                val clientSession = uwbManager.controleeSessionScope()
                // Share the localAddress of the current session to the partner device.
                //broadcastMyParameters(clientSession.localAddress)

                val sessionFlow = clientSession.prepareSession(parameters)

                // Start a coroutine scope that initiates ranging.
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    sessionFlow.collect {
                        when(it) {
                            is RangingResult.RangingResultPosition -> tvRangeDisplay?.setText(it.position.distance.toString())
                            is RangingResult.RangingResultPeerDisconnected -> tvRangeDisplay?.setText("Disconnected")
                            else -> {}
                        }
                    }
                }
            }
            else
            {
                val clientSession2 = uwbManager.controllerSessionScope()
                // Share the localAddress of the current session to the partner device.
                //broadcastMyParameters(clientSession.localAddress)

                val sessionFlow = clientSession2.prepareSession(parameters)

                // Start a coroutine scope that initiates ranging.
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    sessionFlow.collect {
                        when(it) {
                            is RangingResult.RangingResultPosition -> tvRangeDisplay?.text=it.position.distance.toString()
                            is RangingResult.RangingResultPeerDisconnected -> tvRangeDisplay?.text="Disconnected"
                            else -> {}
                        }
                    }
                }
            }




        }*/


    }

    public class myTransportHandle: TransportHandle{
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

    }
    public class myCallback: RangingSession.Callback
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



    class BLETransportHandle(
        private val context: Context,
        private val device: BluetoothDevice,
        private val serviceUUID: UUID,
        private val writeCharUUID: UUID,
        private val notifyCharUUID: UUID
    ) : TransportHandle {

        private var gatt: BluetoothGatt? = null
        private var writeChar: BluetoothGattCharacteristic? = null
        private var notifyChar: BluetoothGattCharacteristic? = null

        @Volatile
        private var connected = false

        private var receiveCallback: ((ByteArray) -> Unit)? = null

        private var connectDeferred: CompletableDeferred<Unit>? = null
        private var writeDeferred: CompletableDeferred<Unit>? = null

        override suspend fun connect() = withContext(Dispatchers.IO) {
            if (connected) return@withContext

            connectDeferred = CompletableDeferred()

            gatt = device.connectGatt(context, false, gattCallback)

            connectDeferred?.await()
        }

        override suspend fun disconnect() = withContext(Dispatchers.IO) {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
            connected = false
        }

        override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
            if (!connected) error("Not connected")

            val characteristic = writeChar ?: error("Write characteristic not found")

            val chunks = chunkData(data)

            for (chunk in chunks) {
                writeDeferred = CompletableDeferred()

                characteristic.value = chunk
                val success = gatt?.writeCharacteristic(characteristic) ?: false

                if (!success) {
                    writeDeferred?.completeExceptionally(Exception("Write failed to start"))
                }

                writeDeferred?.await()
            }
        }

        override fun setOnReceive(callback: (ByteArray) -> Unit) {
            receiveCallback = callback
        }

        override fun isConnected(): Boolean = connected

        // --- BLE Callback ---

        private val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected = false
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(serviceUUID)
                    ?: return connectDeferred?.completeExceptionally(Exception("Service not found"))

                writeChar = service.getCharacteristic(writeCharUUID)
                notifyChar = service.getCharacteristic(notifyCharUUID)

                if (writeChar == null || notifyChar == null) {
                    connectDeferred?.completeExceptionally(Exception("Characteristics missing"))
                    return
                }

                enableNotifications(gatt, notifyChar!!)
                connected = true
                connectDeferred?.complete(Unit)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == notifyCharUUID) {
                    receiveCallback?.invoke(characteristic.value)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeDeferred?.complete(Unit)
                } else {
                    writeDeferred?.completeExceptionally(Exception("Write failed"))
                }
            }
        }

        // --- Helpers ---

        private fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        private fun chunkData(data: ByteArray, chunkSize: Int = 20): List<ByteArray> {
            val chunks = mutableListOf<ByteArray>()
            var i = 0
            while (i < data.size) {
                val end = (i + chunkSize).coerceAtMost(data.size)
                chunks.add(data.copyOfRange(i, end))
                i += chunkSize
            }
            return chunks
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