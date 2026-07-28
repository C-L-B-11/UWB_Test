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
    private val FINS_PACKAGE : Byte = 0b00001010
    private val FINR_PACKAGE : Byte = 0b00001011


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
    private var modeSend : TCPModes = TCPModes.Idle

    private var modeRecv : TCPModes = TCPModes.Idle

    /**
     * Nummer des Paketes, das zuletzt empfangen oder gesendet Wurde
     */
    private var lastPackageIdSend : Byte? = null
    private var lastPackageIdRecv : Byte? = null

    /**
     * Zuletzt gesendetes Packet wird für den Fall des Timeouts zwischengespeichert
     */
    private var lastPackageSend : ByteArray? = null
    private var lastPackageRecv : ByteArray? = null

    /**
     * Timeout timer
     */
    private var repeatTimerSend : Job? = null
    private var repeatTimerRecv : Job? = null

    /**
     * Bringt den Adapter in den Ausgangszustand
     */
    public fun reset(){
        sendMessageBuffer = null
        recvMessageBuffer = null
        modeSend = TCPModes.Idle
        modeRecv = TCPModes.Idle
        lastPackageIdSend = null
        lastPackageIdRecv = null
        repeatTimerSend?.cancel()
        lastPackageSend = null
        repeatTimerRecv?.cancel()
        repeatTimerRecv = null
    }

    /**
     * die Übertragungsschicht meldet den hier den Empfang eines Paketes
     */
    public fun receivedPackage(rPackage:ByteArray):Boolean {
        if(rPackage.size<2)
            return false

        val modeLocal = rPackage[0]
        val packageIdLocal:Byte = rPackage[1]
        val data = rPackage.copyOfRange(2,rPackage.size)

        Log.d("TCP","<${modeSend.ordinal};${modeRecv.ordinal}; ${MainActivity.byteToHexString(rPackage)}")

        return when(modeLocal){
            DATA_PACKAGE -> {
                handleAsReceiver(modeLocal,packageIdLocal,data)
            }
            ACK_PACKAGE -> {
                handleAsSender(modeLocal,packageIdLocal,data)
            }
            FINS_PACKAGE -> {
                handleAsReceiver(modeLocal,packageIdLocal,data)
            }
            FINR_PACKAGE -> {
                handleAsSender(modeLocal,packageIdLocal,data)
            }

            else -> {
                Log.d("TCP","Unknown Mode")
                false
            }
        }
        
    }


    private fun handleAsSender(modeLocal: Byte, packageIdLocal: Byte, data: ByteArray):Boolean{
        lastPackageSend = null             //Bug: timer wird bei jedem empfang gelöscht. Wenn FIN von Recv verloren geht und alter Recv jetzt sendet: endlosschleife
        repeatTimerSend?.cancel()
        repeatTimerSend = null
        when(modeLocal){
            ACK_PACKAGE -> {
                if(packageIdLocal >= totalPackages()-1){
                    outboundPackageSend(byteArrayOf(FINS_PACKAGE,0.toByte()))
                }
                else{
                    lastPackageIdSend = (packageIdLocal + 1).toByte()
                    outboundPackageSend(byteArrayOf(DATA_PACKAGE,lastPackageIdSend!!) + getPackageData(lastPackageIdSend!!.toInt()))
                }
            }
            FINR_PACKAGE -> {
                sendMessageBuffer = null
                lastPackageIdSend = null
                modeSend = TCPModes.Idle
                if(sendMessageBufferQueue.isNotEmpty()){
                    sendMessageBuffer = sendMessageBufferQueue.removeFirst()
                    initSend()
                }
            }
            else ->{
                Log.d("TCP","Unknown Mode for Sender $modeLocal")
                return false
            }
        }
        return true
    }

    private fun handleAsReceiver(modeLocal: Byte, packageIdLocal: Byte, data: ByteArray):Boolean{
        lastPackageRecv = null             //Bug: timer wird bei jedem empfang gelöscht. Wenn FIN von Recv verloren geht und alter Recv jetzt sendet: endlosschleife
        repeatTimerRecv?.cancel()
        repeatTimerRecv = null
        when (modeLocal){
            DATA_PACKAGE -> {
                if(modeRecv == TCPModes.Idle) {
                    modeRecv = TCPModes.Recv
                    recvMessageBuffer = byteArrayOf()
                    lastPackageIdRecv = -1;
                }
                if(packageIdLocal == (lastPackageIdRecv!! + 1.toByte()).toByte()){
                    recvMessageBuffer = recvMessageBuffer!! + data
                    lastPackageIdRecv = packageIdLocal
                }
                outboundPackageRecv(byteArrayOf(ACK_PACKAGE,lastPackageIdRecv!!))
            }
            FINS_PACKAGE -> {
                modeRecv = TCPModes.Idle
                sendPackageFunction(byteArrayOf(FINR_PACKAGE,0.toByte()))
                if(recvMessageBuffer != null)
                    receivedMessageFunction(recvMessageBuffer!!)
                recvMessageBuffer = null
                lastPackageIdRecv = null
            }
            else ->{
                Log.d("TCP","Unknown Mode for Receiver $modeLocal")
                return false
            }
        }
        return true
    }

    /**
     * Die Anwendungsschicht meldet hier das Bedürfnis, eine Nachricht zu senden
     */
    public fun sendMessage(message:ByteArray): Boolean{
        if((sendMessageBuffer!=null) or (modeSend != TCPModes.Idle)){
            Log.d("TCP","Message Added to queue")
            sendMessageBufferQueue.add(message)
            return true
        }
        sendMessageBuffer = message
        initSend()
        return true
    }

    private fun initSend(){
        modeSend = TCPModes.Send
        lastPackageIdSend = 0.toByte()
        outboundPackageSend(byteArrayOf(DATA_PACKAGE,lastPackageIdSend!!) + getPackageData(0))
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
    private fun outboundPackageSend(pack:ByteArray){
        lastPackageSend = pack
        Log.d("TCP",">${modeSend.ordinal}; ${MainActivity.byteToHexString(pack)}")
        sendPackageFunction(pack)

        repeatTimerSend = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if(lastPackageSend != null)
                outboundPackageSend(lastPackageSend!!)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun outboundPackageRecv(pack:ByteArray){
        lastPackageRecv = pack
        Log.d("TCP",">${modeRecv.ordinal}; ${MainActivity.byteToHexString(pack)}")
        sendPackageFunction(pack)

        repeatTimerRecv = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if(lastPackageRecv != null)
                outboundPackageRecv(lastPackageRecv!!)
        }
    }

    enum class TCPModes{
        Idle, Recv, Send
    }

}