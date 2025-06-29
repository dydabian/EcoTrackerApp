package com.example.ecotrackerapp;

public interface BluetoothStatusListener {

    void onStatusChanged(String key, int count, int total);
}
