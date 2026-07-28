package com.example.uwb_test
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.net.LinkProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.uwb_test.MainActivity.RangingTechnology

class WiFiAwareClient(private val context: Context, private val callback: MainActivity.OobConnectionCallback) : MainActivity.OobConnection{



    private var awareSession: WifiAwareSession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var serverPeerHandle: PeerHandle? = null

    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Verbindungs Callback
     */
    private var networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkCallbackClient","onAvailable")
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            // Get the server's link-local IPv6 address from the transport info
            val awareInfo = capabilities?.transportInfo as? WifiAwareNetworkInfo
            val serverAddress: InetAddress? = awareInfo?.peerIpv6Addr

            if (serverAddress != null) {
                Thread { runSocketClient(network, serverAddress) }.start()
            } else {
                Log.d("WiFiAwareClient","Could not resolve server IPv6 address")
            }
        }
        override fun onBlockedStatusChanged(network:Network,blocked:Boolean){
            Log.d("NetworkCallbackClient","onBlockedStatusChanged")
        }
        override fun onLinkPropertiesChanged(network:Network,linkProperties:LinkProperties){
            Log.d("NetworkCallbackClient","onLinkedPropertiesChanged")
        }
        override fun onLosing(network:Network,maxMsToLive:Int){
            Log.d("NetworkCallbackClient","onLosing")
        }
        override fun onLost(network: Network) {
            Log.d("NetworkCallbackClient","onLost")
            stop()
            callback.connectionClosed()
        }
        override fun onReserved(networkCapabilities: NetworkCapabilities){
            Log.d("NetworkCallbackClient","onReserved")
        }
        override fun onUnavailable() {
            Log.d("NetworkCallbackClient","onUnavailable")
            stop()
        }
    }

    /**
     * Callback für den Client zum Melden von gefundenen Servern und deren Nachrichten
     */
    private var discoverySessionCallback= object: DiscoverySessionCallback() {
        override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
            subscribeSession = session
            Log.d("WiFiAwareClient","Subscribed, scanning for $SERVICE_ID")
        }

        override fun onServiceDiscovered(
            peerHandle: PeerHandle,
            serviceSpecificInfo: ByteArray?,
            matchFilter: List<ByteArray>?
        ) {
            Log.d("WiFiAwareClient","Server discovered")
            serverPeerHandle = peerHandle

            // Send an initial message to the server to trigger NDP setup
            subscribeSession?.sendMessage(
                peerHandle, 0, byteArrayOf(0)
            )
        }

        override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
            //Log.d("WiFiAwareClient","Message from server: ${MainActivity.byteToHexString(message)}")
            val mode:Byte = message[0]
            when(mode){
                0.toByte() -> requestConnection(peerHandle)  //Handshake
                else -> {callback.messageReceived(message)}
            }
        }
    }

    init {
        val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                subscribe(session)
            }

            override fun onAttachFailed() {
                println("Client: Wi-Fi Aware attach failed")
            }
        }, null)
    }

    /**
     * Startet das Suchen nach einem Server
     */
    @SuppressLint("MissingPermission")
    private fun subscribe(session: WifiAwareSession) {
        if(!MainActivity.askPermissions(context,*arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)))
            return
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_ID)
            .build()
        session.subscribe(config, discoverySessionCallback,null)
    }

    /**
     * Fragt nach einer Verbindung zum Server
     */
    private fun requestConnection(peerHandle: PeerHandle) {
        Log.d("WifiAwareClient","Requesting NDP connection")

        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(subscribeSession!!, peerHandle)
            .setPskPassphrase("SecurePassphrase123")
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
        connectivityManager.requestNetwork(networkRequest,networkCallback )
    }

    /**
     * Kontaktiert Server, sobald eine Verbindung aufgebaut wurde (aufgerufen von networkCallback.onAvailable())
     */
    private fun runSocketClient(network: Network, serverAddress: InetAddress) {
        Log.d("WiFiAwareClient","Connecting to $serverAddress:8888")

        // Must use network.getSocketFactory() so the socket routes over the Aware NDP
        var socket: Socket? = null
        try {
            socket = network.socketFactory.createSocket(serverAddress.hostAddress, 8888) as Socket
        }
        catch (e:Exception){
            Log.d("WiFiAwareClient","Socket creation failed:${e.message}")
            callback.connectionClosed()
            return
        }
        callback.connectionEstablished()
        val writer = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        writer.write(byteArrayOf(0))
        writer.flush()

        /*val response = reader.*/
        Log.d("WiFiAwareClient","Server responded")

        socket.close()
    }

    /**
     * beendet den Client
     */
    fun stop() {
        subscribeSession?.close()
        awareSession?.close()
    }

    override fun sendMessage(data: ByteArray) {
        //Log.d("WifiAwareClient","Sending message: ${MainActivity.byteToHexString(data)}")
        subscribeSession?.sendMessage(serverPeerHandle!!,0,data)
    }

    override fun isInitiator(): Boolean {
        return false
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
        subscribeSession = null
        serverPeerHandle = null
    }
}