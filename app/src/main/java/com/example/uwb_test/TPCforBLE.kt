package com.example.uwb_test



import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Queue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TPCforBLE (sendPcktFunc_:(ByteArray)->Unit,recvMsgFunc_:(ByteArray)->Unit,pcktLen_:Int){
    private val DATA_PACKAGE :Byte = 0b00001000
    private val ACK_PACKAGE : Byte = 0b00001001
    private val FIN_PACKAGE : Byte = 0b00001010


    /**
     * Wie lang darf ein Packet maximal sein
     */
    private val maxPackageLength = pcktLen_

    /**
     * Callback Methode um ein Packet an das andere Gerät zu senden (Aufruf für übertragungsschicht)
     */
    private val sendPackageFunction = sendPcktFunc_

    /**
     * Callback Methode um eine fertig empfangene Nachricht zurückzugeben (Rückruf für Anwendungsschicht)
     */
    private val receivedMessageFunction = recvMsgFunc_

    /**
     * Buffer für die Übertragung einer Nachricht
     */
    private var sendMessageBuffer:ByteArray? = null

    private var sendMessageBufferQueue: ArrayDeque<ByteArray> = ArrayDeque()

    /**
     * Buffer für die empfangene Nachricht
     */
    private var recvMessageBuffer:ByteArray? = null

    /**
     * Zustand in dem sich der Adapter befindet
     */
    private var modeMain : TCPModes = TCPModes.Idle

    /**
     * Nummer des Paketes, das zuletzt empfangen oder gesendet Wurde
     */
    private var lastPackageId : Byte? = null

    /**
     * Zuletzt gesendetes Packet wird für den Fall des Timeouts zwischengespeichert
     */
    private var lastPackage : ByteArray? = null

    /**
     * Timeout timer
     */
    private var repeatTimer : Job? = null

    /**
     * Bringt den Adapter in den Ausgangszustand
     */
    public fun reset(){
        sendMessageBuffer = null
        recvMessageBuffer = null
        modeMain = TCPModes.Idle
        lastPackageId = null
        lastPackage = null
        repeatTimer?.cancel()
        repeatTimer = null
    }

    /**
     * die Übertragungsschicht meldet den hier den Empfang eines Paketes
     */
    public fun receivedPackage(rPackage:ByteArray):Boolean {
        if(rPackage.size<2)
            return false

        lastPackage = null             //Bug: timer wird bei jedem empfang gelöscht. Wenn FIN von Recv verloren geht und alter Recv jetzt sendet: endlosschleife
        repeatTimer?.cancel()
        repeatTimer = null
        val modeLocal = rPackage[0]
        val packageIdLocal:Byte = rPackage[1]
        val data = rPackage.copyOfRange(2,rPackage.size)

        Log.d("TCP","<${modeMain.ordinal}; ${MainActivity.byteToHexString(rPackage)}")

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

    /**
     * Methode um mit empfangenem Packet im Idle Modus umzugehen
     */
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

    /**
     * Methode um mit empfangenem Packet im Receive Modus umzugehen
     */
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
                if(sendMessageBufferQueue.isNotEmpty()){
                    sendMessageBuffer = sendMessageBufferQueue.removeFirst()
                    initSend()
                }
            }
        }
        return true
    }
    /**
     * Methode um mit empfangenem Packet im Send Modus umzugehen
     */
    private fun handleSend(modeLocal:Byte,packageIdLocal:Byte,data:ByteArray):Boolean{
        when(modeLocal){
            DATA_PACKAGE -> {
                Log.d("TCP","Unexpected DATA for Sender")
                return false
            }

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
                if(sendMessageBufferQueue.isNotEmpty()){
                    sendMessageBuffer = sendMessageBufferQueue.removeFirst()
                    initSend()
                }
            }
        }
        return true
    }

    /**
     * Die Anwendungsschicht meldet hier das Bedürfnis, eine Nachricht zu senden
     */
    public fun sendMessage(message:ByteArray): Boolean{
        if((sendMessageBuffer!=null) or (modeMain != TCPModes.Idle)){
            Log.d("TCP","Message Added to queue")
            sendMessageBufferQueue.add(message)
            return true
        }
        sendMessageBuffer = message
        initSend()
        return true
    }

    private fun initSend(){
        modeMain = TCPModes.Send
        lastPackageId = 0.toByte()
        outboundPackage(byteArrayOf(DATA_PACKAGE,lastPackageId!!) + getPackageData(0))
    }

    /**
     * Schneidet die Daten für eine bestimmte Paket Id aus dem Send Buffer
     */
    private fun getPackageData(id:Int):ByteArray{
        if(id>=totalPackages())
            return byteArrayOf()
        val indexStart = id*(maxPackageLength-2)
        val indexEnd = Math.min((maxPackageLength-2)+indexStart,sendMessageBuffer?.size!!)
        return sendMessageBuffer?.copyOfRange(indexStart,indexEnd)!!
    }

    /**
     * Berechnet in wie viele Pakete die Daten im Send Buffer aufgeteilt werden müssen
     */
    private fun totalPackages(): Int {
        if(sendMessageBuffer == null) return 0
        val dataLen = maxPackageLength-2
        val msgLen = sendMessageBuffer?.size
        return msgLen!!/dataLen +1
    }

    /**
     * Speichert das Packet für einen Resend und kümmert sich um den Timeout Timer
     */
    @OptIn(ExperimentalTime::class)
    private fun outboundPackage(pack:ByteArray){
        lastPackage = pack
        Log.d("TCP",">${modeMain.ordinal}; ${MainActivity.byteToHexString(pack)}")
        sendPackageFunction(pack)

        repeatTimer = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if(lastPackage != null)
                outboundPackage(lastPackage!!)
        }
    }

    enum class TCPModes{
        Idle, Recv, Send
    }

}