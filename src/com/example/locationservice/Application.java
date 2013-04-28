package com.example.locationservice;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.locationservice.LocationService.LocationTracker;

/**
 * 
 * This is the singleton of this application
 * it will receive location update and could
 * be used to propagate the new location
 * 
 * @author francois.legare1
 *
 */
public class Application extends android.app.Application {

	private LocationTracker locationStatus;

	@Override
	public void onCreate() {
		super.onCreate();
	}

	/**
	 * Start location service, we should received location status update soon.
	 * Please note that we didnt check if providers are enabled this should be
	 * done by the activity or else...
	 */
	public void startLocationTracking() {
		Intent i = new Intent(this, LocationService.class);
		i.setAction(LocationService.ACTION_LAUNCH_ALL_PROVIDER);
		startService(i);
	}

	/**
	 * Stop location service
	 */
	public void stopLocationTracking() {
		Intent i = new Intent(this, LocationService.class);
		stopService(i);
	}

	/**
	 * We will receive update here...
	 * 
	 * @param locationStatus
	 */
	public void updateStatusTracker(LocationTracker locationStatus) {

		this.locationStatus = locationStatus;

		// TODO Here call some web service or stuff, for now
		// just displaying stuff in the logs...

		if (locationStatus != null)
			Log.i("" + this.getClass().getName(), locationStatus.toString());
	}

	public LocationTracker getLocationStatus() {
		return locationStatus;
	}

	/**
	 * Check if the className service is running is running
	 * 
	 * @return
	 */
	public boolean isServiceRunning(String className) {

		if (className == null || className.length() == 0) {
			return false;
		}

		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (className.equals(service.service.getClassName())) {
				return true;
			}
		}

		return false;

	}

}
