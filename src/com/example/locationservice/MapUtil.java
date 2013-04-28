package com.example.locationservice;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

public class MapUtil {
	
	/**
	 * Convert a location into LatLng
	 * @param location
	 * @return LatLng
	 */
	public static LatLng converLocToLatLng(Location l){		
		return new LatLng(l.getLatitude(),l.getLongitude()); 
	}
	
	

}
