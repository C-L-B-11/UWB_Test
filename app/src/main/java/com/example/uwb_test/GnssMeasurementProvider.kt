package com.example.uwb_test
import android.app.Activity
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location

import android.location.LocationListener as LocationListenerA
import com.google.android.gms.location.LocationListener as LocationListenerG
import android.location.LocationManager
import com.google.android.gms.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.os.CancellationSignal
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * A container for measurement-related API calls. It binds the measurement providers with the
 * various [com.example.uwb_test.GnssMeasurementListener] implementations.
 */
class GnssMeasurementProvider(
    private val activity: Activity,
    context: Context,
    vararg loggers: GnssMeasurementListener?
) {
    private var mLogLocations = true
    private var mLogNavigationMessages = true
    private var mLogMeasurements = true
    private var mLogStatuses = true
    private var mLogNmeas = true
    private var registrationTimeNanos = 0L
    private var firstLocationTimeNanos = 0L
    private var ttff = 0L
    private var firstTime = true

    private val myExecutor : Executor = Executor { r -> r.run() }

    private val mListeners: MutableList<GnssMeasurementListener>

    val locationManager: LocationManager
    private val mLocationListener: LocationListenerG = object : LocationListenerG {
        override fun onLocationChanged(location: Location) {
            if (firstTime && location.getProvider() == LocationManager.GPS_PROVIDER) {
                if (mLogLocations) {
                    for (logger in mListeners) {
                        firstLocationTimeNanos = SystemClock.elapsedRealtimeNanos()
                        ttff = firstLocationTimeNanos - registrationTimeNanos
                        logger.onTTFFReceived(ttff)
                    }
                }
                firstTime = false
            }
            if (mLogLocations) {
                for (logger in mListeners) {
                    logger.onLocationChanged(location)
                }
            }
        }


    }

    private val mFusedLocationListener: LocationListenerA = object : LocationListenerA {
        public override fun onLocationChanged(location: Location) {
            if (firstTime && location.getProvider() == LocationManager.GPS_PROVIDER) {
                if (mLogLocations) {
                    for (logger in mListeners) {
                        firstLocationTimeNanos = SystemClock.elapsedRealtimeNanos()
                        ttff = firstLocationTimeNanos - registrationTimeNanos
                        logger.onTTFFReceived(ttff)
                    }
                }
                firstTime = false
            }
            if (mLogLocations) {
                for (logger in mListeners) {
                    logger.onLocationChanged(location)
                }
            }
        }
    }

    private val gnssMeasurementsEventListener: GnssMeasurementsEvent.Callback =
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
                if (mLogMeasurements) {
                    for (logger in mListeners) {
                        logger.onGnssMeasurementsReceived(event)
                    }
                }
            }
        }

    private val gnssNavigationMessageListener: GnssNavigationMessage.Callback =
        object : GnssNavigationMessage.Callback() {
            override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage?) {
                if (mLogNavigationMessages) {
                    for (logger in mListeners) {
                        logger.onGnssNavigationMessageReceived(event)
                    }
                }
            }

        }

    private val gnssStatusListener: GnssStatus.Callback = object : GnssStatus.Callback() {
        override fun onStarted() {}

        override fun onStopped() {}

        override fun onFirstFix(ttff: Int) {}

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            for (logger in mListeners) {
                logger.onGnssStatusChanged(status)
            }
        }
    }

    private val nmeaListener: OnNmeaMessageListener = object : OnNmeaMessageListener {
        override fun onNmeaMessage(s: String?, l: Long) {
            if (mLogNmeas) {
                for (logger in mListeners) {
                    logger.onNmeaReceived(l, s)
                }
            }
        }
    }

    init {
        this.mListeners = listOf(*loggers) as MutableList<GnssMeasurementListener>
        this.locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun setLogLocations(value: Boolean) {
        mLogLocations = value
    }

    fun canLogLocations(): Boolean {
        return mLogLocations
    }

    fun setLogNavigationMessages(value: Boolean) {
        mLogNavigationMessages = value
    }

    fun canLogNavigationMessages(): Boolean {
        return mLogNavigationMessages
    }

    fun setLogMeasurements(value: Boolean) {
        mLogMeasurements = value
    }

    fun canLogMeasurements(): Boolean {
        return mLogMeasurements
    }

    fun setLogStatuses(value: Boolean) {
        mLogStatuses = value
    }

    fun canLogStatuses(): Boolean {
        return mLogStatuses
    }

    fun setLogNmeas(value: Boolean) {
        mLogNmeas = value
    }

    fun canLogNmeas(): Boolean {
        return mLogNmeas
    }

    fun registerLocation() {
        val isGpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (isGpsProviderEnabled) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_RATE_NETWORK_MS,
                    0.0f,  /* minDistance */
                    mFusedLocationListener
                )
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_RATE_GPS_MS,
                    0.0f,  /* minDistance */
                    mFusedLocationListener
                )
            } catch (e: SecurityException) {
                // TODO(adaext)
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            }
        }
        logRegistration("LocationUpdates", isGpsProviderEnabled)
    }

    fun unregisterLocation() {
        locationManager.removeUpdates(mFusedLocationListener)
    }

    fun registerFusedLocation() {
        val locationRequest = LocationRequest.Builder(1000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(100)
            .build()
        try {

            LocationServices.getFusedLocationProviderClient(activity).requestLocationUpdates(
                locationRequest,myExecutor, mLocationListener
            )
        } catch (e: SecurityException) {
            // TODO(adaext):
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
    }

    fun unRegisterFusedLocation() {
        LocationServices.getFusedLocationProviderClient(activity).removeLocationUpdates(mLocationListener)
    }

    fun registerSingleNetworkLocation() {
        val isNetworkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (isNetworkProviderEnabled) {
            try {
                val cancellationSignal : CancellationSignal? = null
                val consumer : Consumer<Location?> = Consumer { location -> mLocationListener.onLocationChanged(location!!)}
                locationManager.getCurrentLocation(
                    LocationManager.NETWORK_PROVIDER,cancellationSignal, myExecutor, consumer
                )
            } catch (e: SecurityException) {
                // TODO(adaext):
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            }
        }
        logRegistration("LocationUpdates", isNetworkProviderEnabled)
    }

    fun registerSingleGpsLocation() {
        val isGpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (isGpsProviderEnabled) {
            this.firstTime = true
            registrationTimeNanos = SystemClock.elapsedRealtimeNanos()
            try {
                val cancellationSignal : CancellationSignal? = null
                val consumer : Consumer<Location?> = Consumer { location -> mLocationListener.onLocationChanged(location!!)}
                locationManager.getCurrentLocation(
                    LocationManager.NETWORK_PROVIDER,cancellationSignal, myExecutor, consumer
                )
            } catch (e: SecurityException) {
                // TODO(adaext):
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            }
        }
        logRegistration("LocationUpdates", isGpsProviderEnabled)
    }

    fun registerMeasurements() {
        try {
            logRegistration(
                "GnssMeasurements",
                locationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventListener)
            )
        } catch (e: SecurityException) {
            // TODO(adaext):
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
    }

    fun unregisterMeasurements() {
        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener)
    }

    fun registerNavigation() {
        logRegistration(
            "GpsNavigationMessage",
            locationManager.registerGnssNavigationMessageCallback(gnssNavigationMessageListener)
        )
    }

    fun unregisterNavigation() {
        locationManager.unregisterGnssNavigationMessageCallback(gnssNavigationMessageListener)
    }

    fun registerGnssStatus() {
        try {
            logRegistration(
                "GnssStatus", locationManager.registerGnssStatusCallback(gnssStatusListener)
            )
        } catch (e: SecurityException) {
            // TODO(adaext):
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
    }

    fun unregisterGpsStatus() {
        locationManager.unregisterGnssStatusCallback(gnssStatusListener)
    }

    fun registerNmea() {
        try {
            logRegistration("Nmea", locationManager.addNmeaListener(nmeaListener))
        } catch (e: SecurityException) {
            // TODO(adaext):
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
    }

    fun unregisterNmea() {
        locationManager.removeNmeaListener(nmeaListener)
    }

    fun registerAll() {
        registerLocation()
        registerMeasurements()
        registerNavigation()
        registerGnssStatus()
        registerNmea()
    }

    fun unregisterAll() {
        unregisterLocation()
        unregisterMeasurements()
        unregisterNavigation()
        unregisterGpsStatus()
        unregisterNmea()
    }

    private fun logRegistration(listener: String?, result: Boolean) {
        for (logger in mListeners) {
            logger.onListenerRegistration(listener, result)
        }
    }

    companion object {
        const val TAG: String = "MeasurementProvider"

        private val LOCATION_RATE_GPS_MS = TimeUnit.SECONDS.toMillis(1L)
        private val LOCATION_RATE_NETWORK_MS = TimeUnit.SECONDS.toMillis(60L)
    }
}