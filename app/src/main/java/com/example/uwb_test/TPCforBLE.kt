package com.example.uwb_test



import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TPCforBLE (sendPcktFunc_:(ByteArray)->Unit,recvMsgFunc_:(ByteArray)->Unit,pcktLen_:Int){
    private val DATA_PACKAGE :Byte = 0b00001000
    private val ACK_PACKAGE : Byte = 0b00001001
    private val FIN_PACKAGE : Byte = 0b00001010


    private val maxpackageLenght = pcktLen_
    private val sendPackageFunction = sendPcktFunc_
    private val receivedMessageFunction = recvMsgFunc_


    private var sendMessageBuffer:ByteArray? = null
    private var recvMessageBuffer:ByteArray? = null
    private var modeMain : TCPModes = TCPModes.Idle
    private var lastPackageId : Byte? = null

    private var lastPackage : ByteArray? = null
    private var repeatTimer : Job? = null

    public fun reset(){
        sendMessageBuffer = null
        recvMessageBuffer = null
        modeMain = TCPModes.Idle
        lastPackageId = null
        lastPackage = null
        repeatTimer?.cancel()
        repeatTimer = null
    }

    public fun receivedPackage(rPackage:ByteArray):Boolean {
        if(rPackage.size<2)
            return false

        lastPackage = null
        repeatTimer?.cancel()
        repeatTimer = null
        val modeLocal = rPackage[0]
        val packageIdLocal:Byte = rPackage[1]
        val data = rPackage.copyOfRange(2,rPackage.size)

        return when(modeMain){
            TCPModes.Idle -> {
                handleIdle(modeLocal,packageIdLocal,data)
            }

            TCPModes.Send -> {
                handleSend(modeLocal,packageIdLocal,data)
            }

            TCPModes.Recv -> {
                handleReceive(modeLocal,packageIdLocal,data)
            }
        }
        
    }
    
    private fun handleIdle(modeLocal:Byte,packageIdLocal:Byte,data:ByteArray):Boolean{
        when(modeLocal){
            DATA_PACKAGE -> {
                if( packageIdLocal != 0.toByte()) {
                    Log.d("TCP","Unexpected non 0 Data package in Idle")
                    return false
                }
                modeMain = TCPModes.Recv
                recvMessageBuffer = data
                lastPackageId = packageIdLocal
            }

            ACK_PACKAGE -> {
                Log.d("TCP","Unexpected Ack package in Idle")
                return false
            }

            FIN_PACKAGE -> {
                outboundPackage(byteArrayOf(FIN_PACKAGE, 0.toByte()))
            }
        }
        return true
    }

    private fun handleReceive(modeLocal:Byte, packageIdLocal:Byte, data:ByteArray):Boolean{
        when(modeLocal){
            DATA_PACKAGE -> {
                if(packageIdLocal == (lastPackageId!! + 1.toByte()).toByte()){
                    recvMessageBuffer = recvMessageBuffer!! + data
                    lastPackageId = packageIdLocal
                }
                outboundPackage(byteArrayOf(ACK_PACKAGE,lastPackageId!!))

            }

            ACK_PACKAGE -> {
                Log.d("TCP","Unexpected Ack for Receiver")
                return false
            }

            FIN_PACKAGE -> {
                modeMain = TCPModes.Idle
                sendPackageFunction(byteArrayOf(FIN_PACKAGE,0.toByte()))
                receivedMessageFunction(recvMessageBuffer!!)
                recvMessageBuffer = null
                lastPackageId = null

            }
        }
        return true
    }
    private fun handleSend(modeLocal:Byte,packageIdLocal:Byte,data:ByteArray):Boolean{
        when(modeLocal){
            DATA_PACKAGE -> {return false}

            ACK_PACKAGE -> {
                if(packageIdLocal >= totalPackages()-1){
                    outboundPackage(byteArrayOf(FIN_PACKAGE,0.toByte()))
                }
                else{
                    lastPackageId = (packageIdLocal + 1).toByte()
                    outboundPackage(byteArrayOf(DATA_PACKAGE,lastPackageId!!) + getPackageData(lastPackageId!!.toInt()))
                }
            }

            FIN_PACKAGE -> {
                sendMessageBuffer = null
                lastPackageId = null
                modeMain = TCPModes.Idle
            }
        }
        return true
    }

    public fun sendMessage(message:ByteArray): Boolean{
        if((sendMessageBuffer!=null) or (modeMain != TCPModes.Idle)){
            Log.d("TCP","Buffer not empty:${sendMessageBuffer!=null}, Mode not Idle ${(modeMain != TCPModes.Idle)}")
            return false
        }
        modeMain = TCPModes.Send
        sendMessageBuffer = message
        lastPackageId = 0.toByte()
        outboundPackage(byteArrayOf(DATA_PACKAGE,lastPackageId!!) + getPackageData(0))

        
        return true
    }

    private fun getPackageData(id:Int):ByteArray{
        if(id>=totalPackages())
            return byteArrayOf()
        val indexStart = id*(maxpackageLenght-2)
        val indexEnd = Math.min((maxpackageLenght-2)+indexStart,sendMessageBuffer?.size!!)
        return sendMessageBuffer?.copyOfRange(indexStart,indexEnd)!!
    }

    private fun totalPackages(): Int {
        if(sendMessageBuffer == null) return 0
        val dataLen = maxpackageLenght-2
        val msgLen = sendMessageBuffer?.size
        return msgLen!!/dataLen +1
    }

    @OptIn(ExperimentalTime::class)
    private fun outboundPackage(pack:ByteArray){
        lastPackage = pack
        sendPackageFunction(pack)

        repeatTimer = CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            if(lastPackage != null)
                outboundPackage(lastPackage!!)
        }
    }

    enum class TCPModes{
        Idle, Recv, Send
    }

}