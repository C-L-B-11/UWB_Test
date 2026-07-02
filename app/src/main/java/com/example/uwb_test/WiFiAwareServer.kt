package com.example.uwb_test
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import androidx.annotation.RequiresPermission
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import android.util.Log
import com.example.uwb_test.MainActivity.RangingTechnology
import java.net.InetAddress

class WiFiAwareServer(private val context: Context, private val callback: MainActivity.OobConnectionCallback) : MainActivity.OobConnection{



    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var peerHandle: PeerHandle? = null

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: NetworkCallback? = null
    private var discoverySessionCallback: MyDiscoverySessionCallback? = null

    init {
        val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                publish(session)
            }

            override fun onAttachFailed() {
                Log.d("WiFiAwareServer","Attach failed")
            }
        }, null)
    }


    @SuppressLint("MissingPermission")
    private fun publish(session: WifiAwareSession) {
        if(!MainActivity.askPermissions(context,*arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)))
            return

        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_ID)
            .build()

        discoverySessionCallback = MyDiscoverySessionCallback()
        session.publish(config,  discoverySessionCallback!!,null)
    }

    private fun acceptConnection(peerHandle2: PeerHandle) {
        Log.d("WifiAwareServer","Accept Connection")

        peerHandle = peerHandle2
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(publishSession!!, peerHandle!!)
            .setPskPassphrase("SecurePassphrase123")
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
        networkCallback = NetworkCallback()
        connectivityManager.requestNetwork(networkRequest, networkCallback!!)
    }
    inner class MyDiscoverySessionCallback() : DiscoverySessionCallback() {
        override fun onPublishStarted(session: PublishDiscoverySession) {
            publishSession = session
            Log.d("WiFiAwareServer","Published service $SERVICE_ID")
        }

        override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
            //Log.d("WiFiAwareServer","MessageFromClient: ${MainActivity.byteToHexString(message)}")

            val mode:Byte = message[0]
            val data = message.copyOfRange(1,message.size)
            when(mode){
                0.toByte() ->{acceptConnection(peerHandle)

                    publishSession?.sendMessage(peerHandle, 0,byteArrayOf(0))}
                START_MEASUREMENT -> callback.startMeasuringOrder(RangingTechnology.entries[data[0].toInt()])
                REQUEST_MEASUREMENT -> callback.requestMeasuring(RangingTechnology.entries[data[0].toInt()])
                STOP_MEASUREMENT -> callback.stopMeasuring()
                SHARED_RESULT -> callback.sharedResult(MainActivity.byteArrayToDouble(data))
                DATA_PACKAGE -> {callback.messageReceived(data)}
            }

        }
    }
    inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkCallbackServer","onAvailable")
            Thread { runSocketServer(network) }.start()
        }
        override fun onBlockedStatusChanged(network:Network,blocked:Boolean){
            Log.d("NetworkCallbackServer","onBlockedStatusChanged")
        }
        override fun onLinkPropertiesChanged(network:Network,linkProperties:LinkProperties){
            Log.d("NetworkCallbackServer","onLinkedPropertiesChanged")
        }
        override fun onLosing(network:Network,maxMsToLive:Int){
            Log.d("NetworkCallbackServer","onLosing")
        }
        override fun onLost(network: Network) {
            Log.d("NetworkCallbackServer","onLost")
            stop()
            callback.connectionClosed()
        }
        override fun onReserved(networkCapabilities: NetworkCapabilities){
            Log.d("NetworkCallbackServer","onReserved")
        }
        override fun onUnavailable() {
            Log.d("NetworkCallbackServer","onUnavailable")
        }
    }
    private fun runSocketServer(network: Network) {
        // Port 0 lets the OS assign a free port; share the port via message/OOB if needed.
        // For this example we use a fixed known port.
        var serverSocket : ServerSocket? = null
        try{
            serverSocket = ServerSocket(8888)
        }catch(e:Exception) {
            Log.d("WiFiAwareServer","Socket creation failed:${e.message}")
            callback.connectionClosed()
            return
        }

        Log.d("WiFiAwareServer","Listening on port 8888")

        val clientSocket = serverSocket.accept()

        Log.d("WiFiAwareServer","Client connected from ${clientSocket.inetAddress}")
        callback.connectionEstablished()

        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        val writer = clientSocket.getOutputStream()

        val line = reader.readLine()
        Log.d("WiFiAwareServer","Received over socket: $line")

        writer.write("Echo: $line\n".toByteArray())
        writer.flush()

        clientSocket.close()
        serverSocket.close()
    }

    fun stop() {
        publishSession?.close()
        awareSession?.close()
    }

    override fun sendMessage(data: ByteArray) {
        sendCodedMessage(DATA_PACKAGE,data)
    }

    override fun isInitiator(): Boolean {
        return true
    }

    override fun disconnect() {
        stop()
    }


    override fun startMeasuring(mode:RangingTechnology) {
        sendCodedMessage(START_MEASUREMENT,byteArrayOf(mode.ordinal.toByte()))
    }


    override fun requestMeasuring(mode:RangingTechnology) {
        sendCodedMessage(REQUEST_MEASUREMENT,byteArrayOf(mode.ordinal.toByte()))
    }


    override fun stopMeasuring() {
        sendCodedMessage(STOP_MEASUREMENT,ByteArray(0))
    }


    override fun sharedResult(distance: Double) {
        sendCodedMessage(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }

    fun sendCodedMessage(mode:Byte,message:ByteArray){
        val data = byteArrayOf(mode)+message
        //Log.d("WifiAwareServer","Sending message: ${MainActivity.byteToHexString(data)}")
        publishSession?.sendMessage(peerHandle!!,0,data)
    }

    override fun destroy(){
        connectivityManager.unregisterNetworkCallback(networkCallback!!)
        networkCallback = null
        discoverySessionCallback = null
    }
}