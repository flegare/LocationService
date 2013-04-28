package com.example.locationservice;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

/**
 * 
 * This is just a example of a activity subscribing to the location service intents
 * and extract info out of it...
 * 
 * 
 * @author francois.legare1
 *
 */
public class ShowReportActivity extends Activity {

	private TextView txt;
	private BroadcastReceiver br;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_show_report);
		txt = (TextView) findViewById(R.id.report_txt_bulk);
		
		if(LocationService.getLocationTracker()!=null){
			txt.setText(LocationService.getLocationTracker().toString());
		}
		
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerToLocationUpdate();
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterToLocationUpdate();
	}

	/**
	 * If the view is hidden or not visible
	 * there is no need to update it so
	 * we will simply unregister from the notification
	 * about location update.
	 */
	private void unregisterToLocationUpdate() {
			
		if(br!=null){
			LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
			lbm.unregisterReceiver(br);
		}

	}

	/**
	 * Register a broadcast receiver to receive update notifcation Do not forget
	 * to call unregisterToLocationUpdate when not required
	 */
	private void registerToLocationUpdate() {
		
		//We will create a broadcast receiver to receive notification from
		//the location service...		
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		
		//This is the event we want to receive
		IntentFilter iFilter = new IntentFilter(LocationService.INTENT_LOCATION_UPDATED);
		
		br = new BroadcastReceiver(){

			@Override
			public void onReceive(Context context, Intent intent) {
				updateTheView();				
			}			
		};
		
		//We register to receive these... Again don't forget to unregister! ;)
		lbm.registerReceiver(br,iFilter);		
	}

	/**
	 * This is the place where we should update the activity view. All backend
	 * call should be handle in Application or the service NOT IN A ACTIVITY
	 */
	private void updateTheView() {
		//We could get the location from many place, this is the easiest way
		//since we are on the main thread...
		txt.setText(LocationService.getLocationTracker().toString());
	}

}
