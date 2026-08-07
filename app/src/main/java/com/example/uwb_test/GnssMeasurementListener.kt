package com.example.uwb_test

import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.os.Bundle


/** A class representing an interface for logging a measurement.  */
interface GnssMeasurementListener {
    /** @see LocationListener.onLocationChanged
     */
    fun onLocationChanged(location: Location?)


    /** @see GnssMeasurementsEvent.Callback.onGnssMeasurementsReceived
     */
    fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?)


    /** @see GnssNavigationMessage.Callback.onGnssNavigationMessageReceived
     */
    fun onGnssNavigationMessageReceived(event: GnssNavigationMessage?)


    /** @see GnssStatus.Callback.onSatelliteStatusChanged
     */
    fun onGnssStatusChanged(gnssStatus: GnssStatus?)

    /** Called when the listener is registered to listen to GNSS events  */
    fun onListenerRegistration(listener: String?, result: Boolean)

    /** @see OnNmeaMessageListener.onNmeaMessage
     */
    fun onNmeaReceived(l: Long, s: String?)

    fun onTTFFReceived(l: Long)
}