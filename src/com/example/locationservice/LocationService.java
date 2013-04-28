package com.example.locationservice;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import com.google.android.gms.maps.model.LatLng;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * 
 * This service will keep track location change in background the rational for
 * this service is that we want to get a independent service to keep this
 * tracking even if the activity are no longer visible. This is running on the 
 * main thread son if you add any network operation make sure these are threaded! 
 *
 * 
 * It will notify location change in various way (in preference order)
 * 
 * 1) Send local intent to any broadcast Receiver listening for it. 
 * 2) Update the Application singleton.
 * 3) Call getLocationTracker from somewhere.
 * 
 * @author francois.legare1
 * 
 */
public class LocationService extends Service {

	private static final String TAG = Service.class.getName();

	// Intent signature for subscribing location update
	public static final String INTENT_PROVIDER_STATUS_UPDATE = "com.example.locationservice.statusOfProviderChanged";
	public static final String INTENT_LOCATION_UPDATED = "com.example.locationservice.locationUpdated";
	public static final String EXTRA_LOCATION = "lastLocationReceived";

	// Possible action for this service
	public static final String ACTION_LAUNCH_ALL_PROVIDER = "com.example.locationservice.requestLaunchAllLocationProvider";

	// Configuration constant ////////////
	public static final boolean CFG_USE_GPS = true;
	public static final boolean CFG_USE_NETWORK = true;

	// Notify main application on every change?
	public static final boolean CFG_UPDATE_MAIN_APPLICATION = true;

	// Will send intent on each provider status change (ie. lost of service)
	public static final boolean CFG_BROADCAST_STATUS_CHANGE = true;

	// Will send intent on each location change
	public static final boolean CFG_BROADCAST_LOCATION_CHANGE = true;

	// Get update every 3 sec.
	public static final int CFG_GET_LOCATION_UPDATE_EACH_MS = 3000;

	// Get update only every 5M
	public static final int CFG_GET_LOCATION_UPDATE_EACH_METERS = 0;

	public static final int CFG_MAX_LOCATION_HISTORY = 10; // We keep only the
															// last 50 locations
															// in memory

	// Generate a advance report for the tracker
	private static final boolean CFG_SHOW_FULL_REPORT = true;

	String LR = "\n"; // Line return for report

	private boolean serviceStarted;

	private LocationManager locationManager;

	private LocListner gpsListener;

	private LocListner networkListener;

	private ArrayList<LocListner> activeListener;

	private Application app;

	private static LocationTracker locationStatus;

	/**
	 * Init required variables and bind the location status tracker to this
	 * application
	 */
	public LocationService() {
		activeListener = new ArrayList<LocationService.LocListner>();
		locationStatus = new LocationTracker();
	}

	/**
	 * Return the actual location status
	 * 
	 * @return locationTracker if not set return null
	 */
	public static LocationTracker getLocationTracker() {
		return locationStatus;
	}

	@Override
	public int onStartCommand(Intent i, int flags, int startId) {

		Context ctx = getApplicationContext();
		locationManager = (LocationManager) ctx
				.getSystemService(Context.LOCATION_SERVICE);
		app = (Application) getApplication();

		/*
		 * This is a example where we update the application singleton on every
		 * change...
		 */
		if (CFG_UPDATE_MAIN_APPLICATION) {
			// We give a instance of our location status tracker to the
			// application
			app.updateStatusTracker(locationStatus);
		}

		// TODO Add fine grain action (ex. start/stop GPS only or Network only,
		// etc.)

		if (i.getAction() != null) {
			if (i.getAction().equals(ACTION_LAUNCH_ALL_PROVIDER)) {
				startAllProvider();
			}
		}

		return super.onStartCommand(i, flags, startId);
	}

	/**
	 * Stop listening for location events
	 */
	private void stopAllProviderService() {

		for (int i = 0; i < activeListener.size(); i++) {
			locationManager.removeUpdates(activeListener.get(i));
		}

		activeListener.clear();

		locationStatus.setTrackGPS(false);
		locationStatus.setTrackNetwork(false);
		serviceStarted = false;

		notifyStatusChanged();

		Log.d(TAG, "Removed all localisation listener");

	}

	/**
	 * We will put a listener and record all location update until stopped or
	 * request to stop. We assume provider have been enabled
	 */
	private void startAllProvider() {

		if (serviceStarted)
			return; // ignore this request

		Log.d(TAG, "Starting localisation service");

		serviceStarted = true;

		if (CFG_USE_GPS) {
			gpsListener = new LocListner(LocationManager.GPS_PROVIDER);
			locationStatus.setTrackGPS(registerLocationListner(
					LocationManager.GPS_PROVIDER, gpsListener));
			activeListener.add(gpsListener);
		}

		if (CFG_USE_NETWORK) {
			networkListener = new LocListner(LocationManager.NETWORK_PROVIDER);
			locationStatus.setTrackNetwork(registerLocationListner(
					LocationManager.NETWORK_PROVIDER, networkListener));
			activeListener.add(networkListener);
		}

	}

	/**
	 * Register current service as our location listener
	 * 
	 * @param provider
	 * @param listener
	 * @return if registration was done
	 */
	private boolean registerLocationListner(String provider, LocListner listener) {

		if (locationManager.isProviderEnabled(provider)) {
			locationManager.requestLocationUpdates(provider,
					CFG_GET_LOCATION_UPDATE_EACH_MS,
					CFG_GET_LOCATION_UPDATE_EACH_METERS, listener);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Not used since we want to keep this service
		// running in the background, note that a binder
		// could also be used to get update
		// on location change...
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// We make sure to unregister if service get killed and still running
		if (serviceStarted) {
			stopAllProviderService();
		}

		Log.d(TAG, "Localisation service stopped");
	}

	/**
	 * Will broadcast locally that provider status has changed all broadcast
	 * receiver that subscribed to this type of intent will be notified
	 */
	private void notifyStatusChanged() {

		if (CFG_BROADCAST_STATUS_CHANGE) {
			Intent i = new Intent(INTENT_PROVIDER_STATUS_UPDATE);
			LocalBroadcastManager.getInstance(this).sendBroadcast(i);
		}

		if (CFG_UPDATE_MAIN_APPLICATION) {
			app.updateStatusTracker(locationStatus);
		}

	}

	/**
	 * Will broadcast locally that location has changed all broadcast receiver
	 * that subscribed to this type of intent will be notified
	 */
	private void notifyLocationChanged() {

		if (CFG_BROADCAST_LOCATION_CHANGE) {
			Intent i = new Intent(INTENT_LOCATION_UPDATED);
			// Put location inside the extra (PARCELABLE)
			i.putExtra(EXTRA_LOCATION, locationStatus.getLastLocation());
			LocalBroadcastManager.getInstance(this).sendBroadcast(i);
		}

		if (CFG_UPDATE_MAIN_APPLICATION) {
			app.updateStatusTracker(locationStatus);
		}
	}

	// ///////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Simple listener that will update the location status tracker
	 * 
	 * @author francois.legare1
	 */
	private class LocListner implements LocationListener {

		private String provider;

		public LocListner(String provider) {
			this.provider = provider;
		}

		public String getProvider() {
			return provider;
		}

		@Override
		public void onLocationChanged(Location location) {
			locationStatus.addLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			Log.d(TAG, "onProviderDisabled : " + provider);
			locationStatus.providerHasBeenDisabled(provider);
		}

		@Override
		public void onProviderEnabled(String provider) {

			Log.d(TAG, "onProviderEnabled : " + provider);
			locationStatus.providerHasBeenEnabled(provider);
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.d(TAG, "onStatusChanged : " + provider + " status availble: "
					+ (LocationProvider.AVAILABLE == status));
			locationStatus.updateProviderStatus(provider, status, extras);
		}

	}

	/**
	 * Class to keep track of location update
	 * it can also generate a report based on 
	 * received location update.
	 * 
	 * @author francois.legare1
	 */
	public class LocationTracker {

		private boolean trackGPS;
		private boolean trackNetwork;

		private int gpsProviderStatus;
		private int networkProviderStatus;

		private Location lastLocation;
		private Location lastGpsLocation;
		private Location lastNetworkLocation;
		private Location lastMostPreciseLocation;

		private ArrayList<Location> lastLocations;
		private ArrayList<Location> lastGpsLocations;
		private ArrayList<Location> lastNetworkLocations;

		// Stats variables ////////
		int totalGpsLocRx = 0;
		int totalNetLocRx = 0;

		float maxAccGps = 0;
		float maxAccNet = 0;

		int nbOfOutageForGps = 0;
		int nbOfOutageForNet = 0;

		long lastLocUpdate = 0;
		long lastGpsLocUpdate = 0;
		long lastNetLocUpdate = 0;

		long trackerStarted = System.currentTimeMillis();

		// TODO Add more wonderfull stats

		public LocationTracker() {

			trackGPS = false;
			trackNetwork = false;

			gpsProviderStatus = LocationProvider.OUT_OF_SERVICE;
			networkProviderStatus = LocationProvider.OUT_OF_SERVICE;

			lastLocation = null;
			lastGpsLocation = null;
			lastNetworkLocation = null;
			lastMostPreciseLocation = null;

			lastLocations = new ArrayList<Location>(CFG_MAX_LOCATION_HISTORY);
			lastGpsLocations = new ArrayList<Location>(CFG_MAX_LOCATION_HISTORY);
			lastNetworkLocations = new ArrayList<Location>(
					CFG_MAX_LOCATION_HISTORY);

		}

		/**
		 * Manage provider status events
		 * 
		 * @param provider
		 * @param status
		 * @param extras
		 */
		public void updateProviderStatus(String provider, int status,
				Bundle extras) {

			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				trackGPS = status != LocationProvider.OUT_OF_SERVICE;
				gpsProviderStatus = status;
				if (!trackGPS)
					nbOfOutageForNet++;
				// TODO Keep track of bundle for gps
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
				trackNetwork = status != LocationProvider.OUT_OF_SERVICE;
				networkProviderStatus = status;
				if (!trackNetwork)
					nbOfOutageForNet++;
			}

			notifyStatusChanged();
		}

		/**
		 * Manage provider activation events
		 * 
		 * @param provider
		 */
		public void providerHasBeenEnabled(String provider) {
			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				trackGPS = true;
				gpsProviderStatus = LocationProvider.TEMPORARILY_UNAVAILABLE;
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
				trackNetwork = true;
				networkProviderStatus = LocationProvider.TEMPORARILY_UNAVAILABLE;
			}

			notifyStatusChanged();
		}

		/**
		 * Manage provider disabled events
		 * 
		 * @param provider
		 */
		public void providerHasBeenDisabled(String provider) {
			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				trackGPS = false;
				gpsProviderStatus = LocationProvider.OUT_OF_SERVICE;
				nbOfOutageForGps++;
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
				trackNetwork = false;
				networkProviderStatus = LocationProvider.OUT_OF_SERVICE;
				nbOfOutageForNet++;
			}

			notifyStatusChanged();
		}

		/**
		 * We stack last location and also organise location received
		 * 
		 * @param location
		 */
		public synchronized void addLocation(Location location) {

			lastLocation = location;
			stackLocation(location, lastLocations);			

			if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
				updateGpsLocation(location);
			} else if (LocationManager.NETWORK_PROVIDER.equals(location
					.getProvider())) {
				updateNetLocation(location);
			}

			if (lastMostPreciseLocation == null) {
				lastMostPreciseLocation = location;
			} else if (location.getAccuracy() <= lastMostPreciseLocation
					.getAccuracy()) {
				lastMostPreciseLocation = location;
				// TODO Add time logic and maybe distance from last point logic
			}

			notifyLocationChanged();
		}

		/**
		 * Handle GPS location
		 * 
		 * @param location
		 */
		private void updateGpsLocation(Location location) {

			lastGpsLocation = location;
			lastGpsLocUpdate = System.currentTimeMillis();
			totalGpsLocRx++;

			// TODO Add advance GPS stats (ie. Nb of sat etc)

			if (location.getAccuracy() < maxAccGps)
				maxAccGps = location.getAccuracy();

			stackLocation(location, lastGpsLocations);
		}

		/**
		 * Handle Network location
		 * 
		 * @param location
		 */
		private void updateNetLocation(Location location) {

			lastNetworkLocation = location;
			lastNetLocUpdate = System.currentTimeMillis();
			totalNetLocRx++;

			if (location.getAccuracy() < maxAccGps)
				maxAccNet = location.getAccuracy();

			stackLocation(location, lastNetworkLocations);
		}

		/**
		 * Stack last know location into this array, also insure maximum number
		 * of entry is kept to configured value
		 * 
		 * @param lastKnowLocation
		 * @param arrLoc
		 *            the array of location
		 */
		private void stackLocation(Location l, ArrayList<Location> arrLoc) {

			if (arrLoc.size() >= CFG_MAX_LOCATION_HISTORY) {
				arrLoc.remove(0); // remove first entry (LILO)
			}

			arrLoc.add(l);
		}

		public Location getLastLocation() {
			return lastLocation;
		}

		public ArrayList<Location> getLastLocations() {
			return lastLocations;
		}

		public ArrayList<Location> getLastGpsLocations() {
			return lastGpsLocations;
		}

		public ArrayList<Location> getLastNetworkLocations() {
			return lastNetworkLocations;
		}

		public boolean isTrackGPS() {
			return trackGPS;
		}

		public boolean isGpsLocationAvailable() {
			return lastGpsLocation != null;
		}

		public boolean isNetworkLocationAvailable() {
			return lastNetworkLocation != null;
		}

		public boolean isMostPreciseLocationAvailable() {
			return lastMostPreciseLocation != null;
		}

		public void setTrackGPS(boolean trackGPS) {
			this.trackGPS = trackGPS;
		}

		public boolean isTrackNetwork() {
			return trackNetwork;
		}

		public void setTrackNetwork(boolean trackNetwork) {
			this.trackNetwork = trackNetwork;
		}

		public int getGpsProviderStatus() {
			return gpsProviderStatus;
		}

		public void setGpsProviderStatus(int gpsProviderStatus) {
			this.gpsProviderStatus = gpsProviderStatus;
		}

		public int getNetworkProviderStatus() {
			return networkProviderStatus;
		}

		public void setNetworkProviderStatus(int networkProviderStatus) {
			this.networkProviderStatus = networkProviderStatus;
		}

		public Location getLastGpsLocation() {
			return lastGpsLocation;
		}

		public void setLastGpsLocation(Location lastGpsLocation) {
			this.lastGpsLocation = lastGpsLocation;
		}

		public Location getLastNetworkLocation() {
			return lastNetworkLocation;
		}

		public void setLastNetworkLocation(Location lastNetworkLocation) {
			this.lastNetworkLocation = lastNetworkLocation;
		}

		public ArrayList<Location> getLastLocationsUpdate() {
			return lastLocations;
		}

		public ArrayList<Location> getLastGPSLocationsUpdate() {
			return lastGpsLocations;
		}

		public ArrayList<Location> getLastNetworkLocationsUpdate() {
			return lastNetworkLocations;
		}

		public Location getLastMostPreciseLocation() {
			return lastMostPreciseLocation;
		}
		
		public boolean isLocationAvailable() {
			return lastLocation!=null;
		}

		@Override
		public String toString() {

			String report = "------------------------" + LR;
			report += " Location status report " + LR;
			report += "------------------------" + LR;
			report += "GPS Tracked : " + trackGPS + LR;			
			report += "Net Tracked : " + trackNetwork + LR;			
			report += "Tracker started at : " + new Date(trackerStarted) + LR;
			report += "Tracker started at : " + new Date(trackerStarted) + LR;
			
			if(lastGpsLocation!=null){
				report += "Last gps loc received at : "
						+ new Date(lastGpsLocUpdate) + LR;
				report += "Last GPS location : " +
						new LatLng(lastGpsLocation.getLatitude(),
							       lastGpsLocation.getLongitude()) + LR;
			}
			
			if(lastNetworkLocation!=null){
				report += "Last net loc received at : "
						+ new Date(lastNetLocUpdate) + LR;
				
				report += "Last Network location : " +
						new LatLng(lastNetworkLocation.getLatitude(),
								   lastNetworkLocation.getLongitude()) + LR;
			}
						
			report += "Total GPS Loc received : " + totalGpsLocRx + LR;
			report += "Total Net Loc received : " + totalNetLocRx + LR;
			report += "Max acc for GPS : " + maxAccGps + LR;
			report += "Max acc for Net : " + maxAccNet + LR;
			report += "Nb of outage for GPS : " + nbOfOutageForGps + LR;
			report += "Nb of outage for Net : " + nbOfOutageForNet + LR;
			report += "Loc loc buff size : " + lastLocations.size() + LR;
			report += "GPS loc buff size : " + lastGpsLocations.size() + LR;
			report += "Net loc buff size : " + lastNetworkLocations.size() + LR;

			if (CFG_SHOW_FULL_REPORT) {
				report += " -- Extended report -- " + LR;
				report += generateLocationSummary(lastLocations, "All provider");
				report += generateLocationSummary(lastGpsLocations, "GPS");
				report += generateLocationSummary(lastNetworkLocations,
						"Network");
				report += generateGpsStatusSummary();
			}

			return report;
		}

		/**
		 * Return a summary of the GPS status
		 * 
		 * @return
		 */
		private String generateGpsStatusSummary() {

			String report = "";
			GpsStatus gpsStatus = locationManager.getGpsStatus(null);
			if (gpsStatus != null) {
				report += "**** GPS Status ****" + LR;
				report += "Time for first GPS fix : "
						+ gpsStatus.getTimeToFirstFix() + LR;
				report += "Max GPS sat : " + gpsStatus.getMaxSatellites() + LR;
				int nbOfSat = 0;
				Iterable<GpsSatellite> itr = gpsStatus.getSatellites();
				for (GpsSatellite gpsSatellite : itr) {
					report += "GPS Sat info : " + gpsSatellite + LR;
					nbOfSat++;
				}
				report += "Found : " + nbOfSat + " GPS sat" + LR;
				report += "**** End of GPS Status **** " + LR;
			} else {
				report += "Unable to get GPS status from manager" + LR;
			}

			return report;
		}

		/**
		 * Show average and max acc for this list of location
		 * 
		 * @param locList
		 * @param providerName
		 * @return
		 */
		private String generateLocationSummary(ArrayList<Location> locList,
				String providerName) {

			// TODO Add speed stats too one day

			String report = "";

			int size = locList.size();

			if (size != 0) {

				float sum = 0;
				float avgAcc = 0;
				float maxAcc = 0;

				Iterator<Location> i = locList.iterator();
				while (i.hasNext()) {
					Location location = (Location) i.next();
					float acc = location.getAccuracy();
					sum += acc;
					if (acc < maxAcc || maxAcc == 0) {
						maxAcc = acc;
					}
				}

				avgAcc = sum / size;

				report += providerName + " stats over " + size
						+ " last samples" + LR;
				report += providerName + " average accuracy : " + avgAcc + LR;
				report += providerName + " max accuracy : " + maxAcc + LR;

			} else {
				report += "No sample loc for " + providerName + LR;
			}

			return report;
		}

	
	}
}
