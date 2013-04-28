package com.example.locationservice;

import java.util.ArrayList;
import java.util.Iterator;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.example.locationservice.LocationService.LocationTracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Simple activity to demonstrate a clean way of handling location service
 * 
 * @author francois.legare1
 * 
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MainActivity extends Activity {

	private BroadcastReceiver locUptEventReceiver = null;

	private Application app;

	private TextView acc;

	private ToggleButton toggleButton;

	private boolean showMap;

	private GoogleMap map;

	private LatLng currentPosition;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bindViews();

		// Check if service run already
		app = (Application) getApplication();
		boolean srvIsRunning = app.isServiceRunning(LocationService.class.getName());
		toggleButton.setChecked(srvIsRunning);		
	}

	private void bindViews() {
		toggleButton = ((ToggleButton) findViewById(R.id.main_bt_toggle));
		map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
				.getMap();
	}

	@Override
	protected void onResume() {

		super.onResume();
		// We subscribe for location update
		registerForLocationUpdate();

		// We check if min req is available
		if (checkMinRequirements()) {
			showMap = true;
			updateLocationInformation();
		} else {
			showMap = false;
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		// We make sure to unsubscribe for update
		unregisterForLocationUpdate();
	}

	/**
	 * This will remove notifcation of location change
	 */
	private void unregisterForLocationUpdate() {

		if (locUptEventReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(
					locUptEventReceiver);
			locUptEventReceiver = null;
		}

	}

	/**
	 * This will register a broadcast receiver that alert us on location change,
	 * we will update the view upon receiving these events
	 */
	private void registerForLocationUpdate() {

		locUptEventReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent i) {
				updateLocationInformation();
			}
		};

		// We register this listener, we need to unregister it onPause!
		IntentFilter locUptFilter = new IntentFilter(
				LocationService.INTENT_LOCATION_UPDATED);
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(locUptEventReceiver, locUptFilter);

	}

	/**
	 * Map usage example
	 */
	protected void updateLocationInformation() {

		if (showMap) {
									
			// Update our location on the map
			LocationTracker lt = LocationService.getLocationTracker();
			if (lt!=null && lt.isLocationAvailable()) {

				Location lastLoc = LocationService.getLocationTracker().getLastLocation();				
				currentPosition = MapUtil.converLocToLatLng(lastLoc);

				Log.d("MAP", "Updating map location " + currentPosition);
				BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_launcher);
				
				// Clear old marker (could update the crap too)
				map.clear();								
				map.setMyLocationEnabled(true);
				
				//Mark current location with a black circle (will be overided by bluedot)
				showDotAtLoc(currentPosition, Color.BLUE);
													
				// We put a circle that match accuracy of the last signal
				map.addCircle(new CircleOptions().center(currentPosition)
						.fillColor(Color.argb(75, 0, 0, 255))
						.radius(lastLoc.getAccuracy()).strokeColor(Color.RED)
						.strokeWidth(2));
				
				//Show last received location
				ArrayList<Location> listOfLocation = lt.getLastLocations();
				for (Iterator iterator = listOfLocation.iterator(); iterator.hasNext();) {
					Location location = (Location) iterator.next();
					if(LocationManager.GPS_PROVIDER.equals(location.getProvider())){
						//Draw a red dot for GPS
						showDotAtLoc(MapUtil.converLocToLatLng(location), Color.RED);
					}
					else if(LocationManager.NETWORK_PROVIDER.equals(location.getProvider())){
						//Draw a red dot for GPS
						showDotAtLoc(MapUtil.converLocToLatLng(location), Color.BLUE);
					}
				}
				
				
				
				/*
				// We add a marker of yourself
				map.addMarker(new MarkerOptions().title("You")
						.snippet("You are here!").icon(icon)
						.position(currentPosition));
				*/
									
			}
			
		} else {
			// Show no map...
			findViewById(R.id.main_layout_map).setVisibility(View.GONE);
		}
	}

	/**
	 * Draw a dot at provided location
	 * @param pos
	 * @param color
	 */
	private void showDotAtLoc(LatLng pos, int color ) {		
		map.addCircle(new CircleOptions()
				.center(pos)
				.fillColor(color)
				.radius(0.5)
				.strokeColor(color)
				.strokeWidth(1f));
	}

	private void centerOnMe() {
		map.moveCamera(CameraUpdateFactory.newLatLng(currentPosition));
		map.animateCamera(CameraUpdateFactory.zoomTo(18), 500, null);
	}

	/**
	 * We check if the google play lib is installed
	 * 
	 * @return
	 */
	private boolean checkMinRequirements() {

		switch (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)) {
		case ConnectionResult.SUCCESS:
			return true;
		case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
		case ConnectionResult.SERVICE_MISSING:
		case ConnectionResult.SERVICE_DISABLED:
		case ConnectionResult.SERVICE_INVALID:
		default:
			return false;
		}
	}

	/**
	 * Start and stop location service when clicked
	 */
	public void toggleButtonClicked(View v) {

		if (((ToggleButton) v).isChecked()) {
			// Start the service
			((Application) getApplication()).startLocationTracking();
		} else {
			// Stop the service
			((Application) getApplication()).stopLocationTracking();
		}
	}

	/**
	 * Launch a other activity
	 * 
	 * @param v
	 */
	public void launchOtherActivity(View v) {
		startActivity(new Intent(this, ShowReportActivity.class));
	}
}
