package com.example.uwb_test

abstract class BLESuper :MainActivity.OobConnection{

    /**
     * Liefert die MAC Adresse des anderen Bluetooth Gerätes zurück
     */
    abstract fun getPeerAddress() :String?

    abstract fun getMyAddress() :String?
}