package com.example.uwb_test


import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import android.ranging.oob.TransportHandle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.concurrent.Executor


class MainActivity  : AppCompatActivity() {

    private var exception: TextView? = null

    private var etSessionId: EditText? = null
    private var etSessionKeyInfo: EditText? = null
    private var etSubSessionKeyInfo: EditText? = null
    private var etPartnerAddress: EditText? = null
    private var tvRangeDisplay: TextView? = null
    private var swIsController: Switch? = null
    private var start: Button? = null

    //private var uwbMan: UwbManager? = null
    private var rangingManager :RangingManager? = null

    //private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        rangingManager = baseContext.getSystemService(RangingManager::class.java) as RangingManager
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


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        var bMngr : BluetoothManager = (baseContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
        var list: List<BluetoothDevice> = bMngr.adapter.bondedDevices.toList()
        var i =list.size
        exception?.setText("found $i devices")

        if(i==0)
            return
        var bDev = list[0]
        val rangingDevice = RangingDevice.Builder().setUuid(bDev.uuids[0].uuid).build()
        val transportHandle = myTransportHandle()
        val deviceHandle: DeviceHandle = DeviceHandle.Builder(rangingDevice,transportHandle)
            .build()



        if(swIsController?.isChecked == true) {
            role=RangingPreference.DEVICE_ROLE_INITIATOR
            config = OobInitiatorRangingConfig.Builder().build()
        }
        else
        {
            role = RangingPreference.DEVICE_ROLE_RESPONDER
            config = (OobResponderRangingConfig.Builder( deviceHandle).build()) as RangingConfig;
        }

        var rangingPreference: RangingPreference.Builder =  RangingPreference.Builder(role, config)

        //val myRangingPreference = RangingPreference.Builder();
        mySession?.start(rangingPreference.build())




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