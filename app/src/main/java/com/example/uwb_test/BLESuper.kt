package com.example.uwb_test

import java.util.UUID

interface BLESuper :MainActivity.OobConnection{

    /**
     * Liefert die MAC Adresse des anderen Bluetooth Gerätes zurück
     */
    abstract fun getAddress() :String?
}