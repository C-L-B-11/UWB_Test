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

    /**
     * Verbindung über die Daten geschickt werden
     */
    private var publishSession: PublishDiscoverySession? = null

    /**
     * "Addresse" des anderen Gerätes
     */
    private var peerHandle: PeerHandle? = null

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     *  ??
     */
    private var networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkCallbackServer","onAvailable")
            Thread { runSocketServer() }.start()
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

    /**
     * Callback für den Server zum Melden von gefundenen Clients und deren Nachrichten
     */
    private var discoverySessionCallback=object : DiscoverySessionCallback() {
        override fun onPublishStarted(session: PublishDiscoverySession) {
            publishSession = session
            Log.d("WiFiAwareServer","Published service $SERVICE_ID")
        }

        override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
            //Log.d("WiFiAwareServer","MessageFromClient: ${MainActivity.byteToHexString(message)}")

            val mode:Byte = message[0]
            when(mode){
                0.toByte() ->{acceptConnection(peerHandle)                                      //Handshake
                    publishSession?.sendMessage(peerHandle, 0,byteArrayOf(0))}
                else -> {callback.messageReceived(message)}
            }

        }
    }

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

    /**
     * Veröffentlicht den Service
     */
    @SuppressLint("MissingPermission")
    private fun publish(session: WifiAwareSession) {
        if(!MainActivity.askPermissions(context,*arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)))
            return

        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_ID)
            .build()

        session.publish(config,  discoverySessionCallback,null)
    }

    /**
     * Akzeptiert die Verbindung von einem anderen Gerät, wird von discoverySessionCallback aufgerufen, wenn eine Handshake Nachricht eingeht
     */
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
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }

    /**
     * finalisiert den server, wird aufgerufen, wenn onAvailable() vom Network Callback aufgerufen wird
     */
    private fun runSocketServer() {
        var serverSocket : ServerSocket?
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

    /**
     * beendet den Server
     */
    fun stop() {
        publishSession?.close()
        awareSession?.close()
    }

    override fun sendMessage(data: ByteArray) {
        //Log.d("WifiAwareServer","Sending message: ${MainActivity.byteToHexString(data)}")
        publishSession?.sendMessage(peerHandle!!,0,data)
    }

    override fun isInitiator(): Boolean {
        return true
    }

    override fun disconnect() {
        stop()
    }

    /**
     * gibt alles frei
     */
    override fun destroy(){
        connectivityManager.unregisterNetworkCallback(networkCallback)
        awareSession = null
        publishSession = null
        peerHandle = null
    }
}