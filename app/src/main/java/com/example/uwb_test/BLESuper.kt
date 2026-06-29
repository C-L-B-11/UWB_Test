package com.example.uwb_test

interface BLESuper :MainActivity.OobConnection{

    abstract fun getAddress() :String?
}