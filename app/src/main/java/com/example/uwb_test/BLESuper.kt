package com.example.uwb_test

import java.util.UUID

interface BLESuper :MainActivity.OobConnection{

    abstract fun getAddress() :String?
}