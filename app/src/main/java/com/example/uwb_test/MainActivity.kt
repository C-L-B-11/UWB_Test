package com.example.uwb_test


import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
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
        start!!.setOnClickListener { v: View? -> connect() }

    }

    private fun connect() {
        if (!getPackageManager().hasSystemFeature("android.hardware.uwb")) {
            exception?.setText("Uwb feature not available")
            return
        }

        val uwbManager = UwbManager.createInstance(baseContext)
        scope.launch {
            val sessionScope: UwbClientSessionScope = uwbManager.clientSessionScope()
            val ucc: UwbComplexChannel = UwbComplexChannel(
                channel = 1,
                preambleIndex = 1
            )
            val parameters = RangingParameters(
                uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
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
        var x : ByteArray = ByteArray(8)

        if(s.length!=16)
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