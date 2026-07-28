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
import com.example.uwb_test.MainActivity.RangingTechnology

class WiFiDirect(private val contextM: Context, private val callback: MainActivity.OobConnectionCallback,private val isHost:Boolean): MainActivity.OobConnection {


    private val STRATEGY = Strategy.P2P_STAR


    private var connectionsClient: ConnectionsClient? = null

    /**
     * Gibt informationen über die Verbindung zurück
     */
    private var connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
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

    /**
     * Callback für die Übertragung von Daten
     */
    private var payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes: ByteArray? = payload.asBytes()
                if (receivedBytes != null) {
                    // Successfully received your custom ByteArray!
                    callback.messageReceived(receivedBytes)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Monitor transfer progress or track completion states
        }
    }

    /**
     * Teilt dem Scanner mit das ein Advertiser gefunden wurde
     */
    private var endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Automatically request a connection to the discovered host
            connectionsClient?.requestConnection("Pixel 10 Client", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            callback.connectionClosed()
        }
    }

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

    /**
     * Startet die Veröffentlichung als Advertiser
     */
    private fun startAdvertising() {
        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (!askPermissions(contextM, *permissions.toTypedArray())) {
            Log.d("WifiDirekt", "no Permission")
            callback.statusMessage("Missing permissions for Advertising")
            callback.connectionClosed()
            return
        }

        connectionsClient?.stopAdvertising()
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient?.startAdvertising(
            "Pixel 10 Host", // User-friendly device name visible to the other phone
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )?.addOnSuccessListener {
            Log.d("WifiDirekt", "Advertising started")
        }?.addOnFailureListener { e ->
            Log.e("WifiDirekt", "Advertising failed", e)
            callback.statusMessage("Failed to start advertising: ${e.message}")
            callback.connectionClosed()
        }
    }

    /**
     * Startet die Suche nach einem Host
     */
    private fun startDiscovering() {
        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (!askPermissions(contextM, *permissions.toTypedArray())) {
            Log.d("WifiDirect", "no Permission")
            callback.statusMessage("Missing permissions for Discovery")
            callback.connectionClosed()
            return
        }

        connectionsClient?.stopDiscovery()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        if (connectionsClient == null) {
            Log.d("WifiDirect", "no connectionsClient")
            callback.connectionClosed()
            return
        }
        connectionsClient?.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )?.addOnSuccessListener {
            Log.d("WifiDirect", "Discovery started")
        }?.addOnFailureListener { e ->
            Log.e("WifiDirect", "Discovery failed", e)
            callback.statusMessage("Failed to start discovery: ${e.message}")
            callback.connectionClosed()
        }
    }


    override fun sendMessage(data: ByteArray) {
        if(connectionLifecycleCallback.endpointId==null){
            Log.d("WifiDirekt", "Can not send Data, no endpointId")
            return
        }
        val payload = Payload.fromBytes(data)
        connectionsClient?.sendPayload(connectionLifecycleCallback.endpointId!!, payload)?.addOnFailureListener { e -> /* Handle send failure */ }
    }

    override fun isInitiator(): Boolean {
        return !isHost
    }

    override fun disconnect() {
        if(connectionLifecycleCallback.endpointId!=null)
            connectionsClient?.disconnectFromEndpoint(connectionLifecycleCallback.endpointId!!)
        callback.connectionClosed()
    }
}