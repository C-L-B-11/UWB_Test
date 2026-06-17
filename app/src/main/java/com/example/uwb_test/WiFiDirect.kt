package com.example.uwb_test

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.example.uwb_test.MainActivity.Companion.askPermissions

class WiFiDirect(private val contextM: Context, private val callback: MainActivity.OobConnectionCallback,private val isHost:Boolean): MainActivity.OobConnection {

    private val DATA_PACKAGE :Byte = 0b00001000
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    private val SERVICE_ID = "com.example.myapp.DATA_EXCHANGE_SERVICE"
    private var connectionsClient: ConnectionsClient? = null
    private var connectionLifecycleCallback = MyConnectionLifecycleCallback()
    private var payloadCallback = MyPayloadCallback()
    private var endpointDiscoveryCallback = MyEndpointDiscoveryCallback()

    init{


        connectionsClient = Nearby.getConnectionsClient(contextM)
        if(connectionsClient==null){
            Log.d("WifiDirekt", "no Client")
        }
        else {
            if (isHost) {
                startAdvertising()
            } else {
                startDiscovering()
            }
        }

    }

    private fun startAdvertising() {
        if(!askPermissions(contextM,*arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.NEARBY_WIFI_DEVICES))){
            Log.d("WifiDirekt", "no Permission")
        }
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient?.startAdvertising(
            "Pixel 10 Host", // User-friendly device name visible to the other phone
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )?.addOnSuccessListener {
            // Advertising successfully started, waiting for discoverer
        }?.addOnFailureListener { e ->
            // Handle failure
        }
    }
    private fun startDiscovering() {
        if(!askPermissions(contextM,*arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.NEARBY_WIFI_DEVICES))){
            Log.d("WifiDirekt", "no Permission")
        }
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        if(connectionsClient==null){
            Log.d("WifiDirekt", "no connectionsClient")
            return
        }
        connectionsClient?.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )?.addOnSuccessListener {
            // Discovery successfully started, searching for host
        }?.addOnFailureListener { e ->
            // Handle failure
        }
    }
    // Used by the Discoverer to detect the Advertiser
    private inner class MyEndpointDiscoveryCallback : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Automatically request a connection to the discovered host
            connectionsClient?.requestConnection("Pixel 10 Client", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    // Used by both devices to manage the lifecyle of the active request
    private inner class MyConnectionLifecycleCallback: ConnectionLifecycleCallback() {
        public var endpointId: String? = null

        override fun onConnectionInitiated(endpointIdL: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection request on both ends
            connectionsClient?.acceptConnection(endpointIdL, payloadCallback)
        }

        override fun onConnectionResult(endpointIdL: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                // Connection established! Stop scanning/advertising to save battery
                connectionsClient?.stopAdvertising()
                connectionsClient?.stopDiscovery()
                this.endpointId = endpointIdL
                // You can now safely pass your data using this endpointId
                callback.connectionEstablished()
            }
        }

        override fun onDisconnected(endpointId: String) {
            // Handle intentional or unexpected disconnects
            callback.connectionClosed()
        }
    }

    private fun sendCustomByteArray( mode:Byte, data: ByteArray) {
        if(connectionLifecycleCallback.endpointId==null){
            Log.d("WifiDirekt", "Can not send Data, no endpointId")
            return
        }
        val payload = Payload.fromBytes(byteArrayOf(mode) + data)
        connectionsClient?.sendPayload(connectionLifecycleCallback.endpointId!!, payload)?.addOnFailureListener { e -> /* Handle send failure */ }
    }

    inner class MyPayloadCallback  : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes: ByteArray? = payload.asBytes()
                if (receivedBytes != null) {
                    // Successfully received your custom ByteArray!
                    val mode:Byte = receivedBytes[0]
                    var data = receivedBytes.copyOfRange(1,receivedBytes.size)
                    when(mode){
                        START_MEASUREMENT -> callback.startMeasuringOrder()
                        REQUEST_MEASUREMENT -> callback.requestMeasuring()
                        STOP_MEASUREMENT -> callback.stopMeasuring()
                        SHARED_RESULT -> callback.sharedResult(MainActivity.byteArrayToDouble(data))
                        DATA_PACKAGE -> {callback.messageReceived(data)}
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Monitor transfer progress or track completion states
        }
    }





    override fun sendMessage(data: ByteArray) {
        sendCustomByteArray(DATA_PACKAGE,data)
    }

    override fun isInitiator(): Boolean {
        return !isHost
    }

    override fun disconnect() {
        if(connectionLifecycleCallback.endpointId!=null)
            connectionsClient?.disconnectFromEndpoint(connectionLifecycleCallback.endpointId!!)
        callback.connectionClosed()
    }

    override fun startMeasuring() {
        sendCustomByteArray(START_MEASUREMENT,byteArrayOf(0))
    }

    override fun requestMeasuring() {
        sendCustomByteArray(REQUEST_MEASUREMENT,byteArrayOf(0))
    }

    override fun stopMeasuring() {
        sendCustomByteArray(STOP_MEASUREMENT,byteArrayOf(0))
    }

    override fun sharedResult(distance: Double) {
        sendCustomByteArray(SHARED_RESULT,MainActivity.doubleToByteArray(distance))
    }
}