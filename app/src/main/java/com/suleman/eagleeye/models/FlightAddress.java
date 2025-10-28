package com.suleman.eagleeye.models;

import java.io.Serializable;

public class FlightAddress implements Serializable {

    public double lat;
    public double lng;
    public FlightAddress(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }



}