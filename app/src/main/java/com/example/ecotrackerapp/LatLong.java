package com.example.ecotrackerapp;

import java.time.LocalDateTime;

public class LatLong {
    public final double lat;
    public final double lng;
    private final LocalDateTime timestamp;

    LatLong(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = LocalDateTime.now();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public String getCSVString() {
        return this.lat + "," + this.lng;
    }
}

