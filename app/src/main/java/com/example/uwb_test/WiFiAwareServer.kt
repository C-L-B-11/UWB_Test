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
import java.net.InetAddress

class WiFiAwareServer(private val context: Context, private val callback: MainActivity.OobConnectionCallback) : MainActivity.OobConnection{



    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null

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

    private fun acceptConnection(peerHandle: PeerHandle) {
        Log.d("WifiAwareServer","Accept Connection")


        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(publishSession!!, peerHandle)
            .setPskPassphrase("SecurePassphrase123")
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        connectivityManager.requestNetwork(networkRequest, NetworkCallback())
    }
    inner class MyDiscoverySessionCallback() : DiscoverySessionCallback() {
        override fun onPublishStarted(session: PublishDiscoverySession) {
            publishSession = session
            Log.d("WiFiAwareServer","Published service $SERVICE_ID")
        }

        override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
            Log.d("WiFiAwareServer","MessageFromClient: ${MainActivity.byteToHexString(message)}")

            val mode:Byte = message[0]
            val data = message.copyOfRange(1,message.size)
            when(mode){
                0.toByte() ->{acceptConnection(peerHandle)

                    publishSession?.sendMessage(peerHandle, 0,byteArrayOf(0))}
                START_MEASUREMENT -> callback.startMeasuringOrder()
                REQUEST_MEASUREMENT -> callback.requestMeasuring()
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
        val serverSocket = ServerSocket(8888)
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
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
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


    override fun startMeasuring() {
        sendCodedMessage(START_MEASUREMENT,ByteArray(0))
    }


    override fun requestMeasuring() {
        sendCodedMessage(REQUEST_MEASUREMENT,ByteArray(0))
    }


    override fun stopMeasuring() {
        sendCodedMessage(STOP_MEASUREMENT,ByteArray(0))
    }


    override fun sharedResult(distance: Double) {
        sendCodedMessage(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }

    fun sendCodedMessage(mode:Byte,message:ByteArray){

    }
}