package com.example.ecotrackerapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.content.BroadcastReceiver;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;


import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textview.MaterialTextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity implements BluetoothStatusListener, BluetoothRequestCallback{

    private BluetoothScanner bleScanner;
    private DrawerLayout drawerLayout;
    private LogFile currentLog;
    private static final int MODE_START_LOGGING = 1;
    private static final int MODE_LOGGING = 2;
    private static final int MODE_STOP_LOGGING = 3;
    private static final int MODE_GET_SETTINGS = 4;

    private LocalDateTime trackingStartTime;
    private int startAttempts;
    private int downloadAttempts;
    private ArrayList<LatLong> locationsList;
    private int currentMode;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private boolean locationEnabled = false;
    private boolean fineLocationGranted = false;
    private boolean backgroundLocationGranted = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        setNavigation(toolbar);
        setHomeScreen();
        setCancelHoldMode();
        checkLocation(this);
        checkLocationPermissions();
        currentMode = MODE_START_LOGGING;
    }


    private void checkLocation(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_on =locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network_on =locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        locationEnabled = gps_on || network_on;

        if (!locationEnabled) {
            checkLocationServiceStatus();
        }
    }

    private void checkLocationPermissions() {

        fineLocationGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        backgroundLocationGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineLocationGranted) {
            requestLocationPermissions();
        }
        else if (!backgroundLocationGranted){
            requestBackgroundLocation();
        }
    }
    private void requestLocationPermissions() {
        // Android 10+ requires separate request for background location
        ActivityCompat.requestPermissions(this,
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void requestBackgroundLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            new AlertDialog.Builder(this)
                    .setTitle("Background Location Required")
                    .setMessage("This app needs background location to work properly.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
    private void setNavigation(Toolbar toolbar) {
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle menuToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(menuToggle);
        menuToggle.syncState();

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (currentMode == MODE_LOGGING || currentMode == MODE_STOP_LOGGING) {
                showAlert("Command Blocked", getString(R.string.command_block));
                DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (id == R.id.nav_home) {
                setHomeScreen();
            }
            else if (id == R.id.nav_settings) {
                getSensorSettings();
            }
            else if (id == R.id.nav_email) {
                sendDataByEmail();
            }
            else if (id == R.id.nav_close) {
                currentLog.addEntry("app closed");
                finishAffinity();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }
    private void setHomeScreen() {
        currentMode = MODE_START_LOGGING;
        setStatus(getString(R.string.home));
        setMainButtonText(R.string.button_start);
        enableMainButton();
        hideCancelButton();
        setClickMode();
        startAttempts=0;
    }

    private void buttonMainClick() {
        switch (currentMode) {
            case MODE_START_LOGGING:
                startLogging();
                break;
            case MODE_STOP_LOGGING:
                hideCancelButton();
                downloadSensorData();
                break;
            default:
                break;
        }
    }

    private void hideCancelButton() {
        findViewById(R.id.buttonCancel).setVisibility(View.GONE);
    }
    private void showCancelButton() {
        findViewById(R.id.buttonCancel).setVisibility(View.VISIBLE);
    }
    private void disableMainButton() {
        findViewById(R.id.buttonMain).setEnabled(false);
    }
    private void enableMainButton() {
        findViewById(R.id.buttonMain).setEnabled(true);
    }
    private void setLogFile() {
        currentLog = new LogFile(getFilesDir().getAbsolutePath());
        currentLog.addEntry("application started");
    }

    private void sendDataByEmail() {
        if (!isOnline(this)) {
            showAlert("No connection","Turn on internet connection");
            return;
        }

        //currentMode = MODE_SEND_DATA;
        currentLog.addEntry("send log by e-mail");
        SendEmail send = new SendEmail(this, currentLog);
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }
    private void getSensorSettings() {
        currentMode = MODE_GET_SETTINGS;
        if (bluetoothIsReady()) {
            currentLog.addEntry("get sensor settings");
            CompletableFuture.supplyAsync(() -> bleScanner.getSensorSettings())
                    .thenAccept(result -> {
                        runOnUiThread(() -> showCommandResult(result));
                    });
        }
    }

    private boolean bluetoothIsReady() {
        if (bleScanner==null ||!bleScanner.hasSensor()) {
            scanForSensors();
            //setStatus("Temperature sensor has not been selected. \n\n Select Scan for Sensors first.");
            return false;
        }

        return true;
    }
    private void clearSensorData() {
        if (bluetoothIsReady()) {
                currentLog.addEntry("clear sensor");
                CompletableFuture.supplyAsync(() -> bleScanner.clearData())
                        .thenAccept(result -> {
                            runOnUiThread(() -> showCommandResult(result));
                        });
        }
    }

    private void startLogging() {
        checkLocation(this);
        checkLocationPermissions();

        if (!locationEnabled) {
            showAlert("Location Is Off", "Location must be on to track GPS");
            return;
        }
        else if(!fineLocationGranted || !backgroundLocationGranted) {
            showAlert("Location Permissions", "Location permission is required to track GPS");
            return;
        }

        setLogFile();
        startAttempts++;
        scanForSensors();
        startGpsTracking();
    }

    private void stopLogging() {
        MaterialButton btn = findViewById(R.id.buttonMain);
        btn.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
        setStatus(getString(R.string.stop_logging));
        setMainButtonText(R.string.button_download);
        setClickMode();
        enableMainButton();
        setCancelClickMode();
        showCancelButton();
        currentMode = MODE_STOP_LOGGING;
        downloadAttempts = 0;
    }
    private void showCommandResult(boolean success) {
        if (success) {
            switch (currentMode) {
                case MODE_START_LOGGING:
                    currentLog.addEntry("Sensor cleared");
                    setStatus("Sensor cleared");
                    startGpsTracking();
                    break;
                case MODE_LOGGING:
                    stopGpsTracking();
                    break;
                case MODE_GET_SETTINGS:
                    setStatus("Sensor Settings\n\n" + bleScanner.getSensorResponse());
                    break;
                default:
                    break;
            }
        }
        else {
            switch (currentMode) {
                case MODE_START_LOGGING:

                    if (startAttempts < 4) {
                        setStatus("Start attempt failed.  Retrying.");
                        startLogging();
                    }
                    else {
                        setStatus("Failed to initialize tracking.  \n\n Tap Start again.");
                        enableMainButton();
                    }
                    break;
                  case MODE_GET_SETTINGS:
                    showAlert("Error", "View settings command failed");
                    setHomeScreen();
                    break;
                default:
                    break;
            }
        }
    }

    private void setStatus(String msg) {
        MaterialTextView txtView = findViewById(R.id.textStatus);
        txtView.setText(msg);
    }
    private void downloadSensorData() {

        if (bluetoothIsReady()) {
            downloadAttempts++;
            disableMainButton();
            currentLog.addEntry("download requested");
            CompletableFuture.supplyAsync(() -> bleScanner.downloadData())
                    .thenAccept(result -> {
                        runOnUiThread(() -> saveDownload(result));
                    });
        }
    }

    private void saveDownload(boolean downloadSuccess ) {

        if (!downloadSuccess) {
            currentLog.addEntry("Download failed");
            if (downloadAttempts < 4) {
                setStatus(getString(R.string.download_fail));
                downloadSensorData();
            }
            else {
                showAlert("Error", "Download failed. Make sure sensor and Bluetooth are on.");
                enableMainButton();
            }
            return;
        }

        int count = currentLog.writeTemperatureData(bleScanner.getSensorName(), bleScanner.getSensorData());
        Toast.makeText(this,count + " readings downloaded", Toast.LENGTH_SHORT).show();
        stopGpsTracking();
        setHomeScreen();
    }

    private void scanForSensors() {
        bleScanner = new BluetoothScanner(this, this);

        if (!bleScanner.isSupported()) {
            showAlert("Error", "Bluetooth is not supported on this device.  The app will not work");
            return;
        }

        if (!bleScanner.hasCorrectPermissions()) {
            bleScanner.requestPermissions();
            return;
        }

        if (!bleScanner.enabled()) {
            bleScanner.promptUserToEnable(this);
            return;
        }
        disableMainButton();
        currentLog.addEntry("Scanning for sensors");
        bleScanner.scan(devices -> showScanResults());
    }

    private void showScanResults() {
        List<BluetoothDevice> devices = bleScanner.getDeviceList();

        if (devices.isEmpty()) {
            setStatus(getString(R.string.scan_failed));
            currentLog.addEntry("No sensors found");
            enableMainButton();
        }
        else if (devices.size() == 1) {
            bleScanner.setSensor(devices.get(0));
            currentLog.addEntry("Found sensor address: " + devices.get(0).getAddress() + " name:" + devices.get(0).getName());

            if (currentMode == MODE_START_LOGGING) {
                setStatus(getString(R.string.clear_sensor));
                clearSensorData();
            }
            else if (currentMode == MODE_GET_SETTINGS) {
                getSensorSettings();
            }
        }
        else {
            setStatus(getString(R.string.scan_many));
            enableMainButton();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void startGpsTracking() {
        this.startForegroundService(new Intent(this, LocationService.class));
        IntentFilter filter = new IntentFilter("com.example.gps_tracker.LOCATION_UPDATE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            registerReceiver(locationReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(locationReceiver, filter);
        }
        //registerReceiver(locationReceiver, filter);
        if (locationsList == null){
            locationsList = new ArrayList<>();
        }
        else {
            locationsList.clear();
        }

        setLoggingScreen();
        trackingStartTime = LocalDateTime.now();
        currentLog.addEntry("tracking started");
        setStatus(getString(R.string.logging_start));
      }

    private void setLoggingScreen() {
        currentMode = MODE_LOGGING;
        setMainButtonText(R.string.button_stop);
        enableMainButton();
        setStopMode();
        setCancelHoldMode();
        showCancelButton();
    }

    private void setClickMode() {
        MaterialButton btn = findViewById(R.id.buttonMain);
        btn.setOnTouchListener(null);
        btn.setOnClickListener(v -> {
            buttonMainClick();
        });
    }

    private void setCancelClickMode() {
        MaterialButton btn = findViewById(R.id.buttonCancel);
        btn.setOnTouchListener(null);
        btn.setOnClickListener(v -> {
            buttonCancelClick();
        });
    }
    private void buttonCancelClick() {
        updateLoggingDisplay();
        setLoggingScreen();
    }
    private void setCancelHoldMode() {
        //set cancel button press and hold behavior
        MaterialButton btn = findViewById(R.id.buttonCancel);
        btn.setOnClickListener(null);
        Handler handler = new Handler();
        Runnable holdRunnable = this::cancelLogging;

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    btn.setBackgroundColor(Color.RED);
                    handler.postDelayed(holdRunnable, 3000);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    btn.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
                    handler.removeCallbacks(holdRunnable);
                    return true;
            }
            return false;
        });
    }
    private void setStopMode() {
        //when logging set main button press and hold for 3 seconds to stop
        MaterialButton btn = findViewById(R.id.buttonMain);
        btn.setOnClickListener(null);
        Handler handler = new Handler();
        Runnable holdRunnable = this::stopLogging;

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    btn.setBackgroundColor(Color.RED);
                    handler.postDelayed(holdRunnable, 3000);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    btn.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
                    handler.removeCallbacks(holdRunnable);
                    return true;
            }
            return false;
        });
    }

    private void cancelLogging() {
        currentLog.addEntry("tracking cancelled");
        stopGpsTracking();
        setHomeScreen();
        Toast.makeText(this, "Logging was cancelled", Toast.LENGTH_SHORT).show();
    }
    private void setMainButtonText(int id) {
        MaterialButton btn = findViewById(R.id.buttonMain);
        btn.setText(getString(id));
    }

    private void stopGpsTracking() {
        this.stopService(new Intent(this, LocationService.class));
        unregisterReceiver(locationReceiver);
        Log.d("GPS", "tracking stopped");
    }

    private void showAlert(String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // User confirmed
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        //dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FFFFFF"));
    }


    @Override
    public void onStatusChanged(final String key, int count, int total) {
        MaterialTextView txtView = findViewById(R.id.textStatus);

        runOnUiThread(new Runnable() {
            public void run() {
                switch (key) {
                    case "scanning":
                        txtView.setText(R.string.scanning);
                        break;
                    case "connecting":
                        txtView.setText(R.string.connect);
                        break;
                    case "sending":
                        txtView.setText(R.string.sending);
                        break;
                    case "connected" :
                        txtView.setText(getString(R.string.connected));
                        break;
                    case "disconnect":
                        txtView.setText(getString(R.string.disconnect));
                        break;
                    case "send fail":
                        txtView.setText(getString(R.string.command_send_fail));
                        break;
                    case "connection fail":
                        txtView.setText(getString(R.string.connect_fail));
                        break;
                    case "downloading":
                        txtView.setText(getString(R.string.downloading, count, total));
                        break;
                }
            }
        });
    }

    private void checkLocationServiceStatus() {
        LocationRequest locationRequest = new LocationRequest.Builder(10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true); // Shows dialog even if settings are satisfied partially

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            // ✅ Location settings are ON — proceed with app

        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(this, 1001);
                } catch (IntentSender.SendIntentException sendEx) {
                    Log.e("E", "Error reading file", e);
                }
            }
        });
    }

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            double lat = intent.getDoubleExtra("lat", 0);
            double lng = intent.getDoubleExtra("lng", 0);
            Log.d("MainActivity", "Received location: " + lat + ", " + lng);
            LatLong point = new LatLong(lat, lng);
            locationsList.add(point);
            currentLog.addEntry(point.getCSVString());

            if (currentMode == MODE_LOGGING) {
                updateLoggingDisplay();
            }
        }
    };

    private void updateLoggingDisplay() {
        LocalDateTime currentTime = LocalDateTime.now();
        String str = "Logging started at: " + trackingStartTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "\n" +
                "Last reading: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "\n" +
                "Count: " + locationsList.size() + "\n\n" +
                getString(R.string.logging);

        setStatus(str);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showAlert("Location Denied", "Location access is required for GPS tracking");
            }
        }
        else if (requestCode == 1001) {
            //bluetooth
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showAlert("Bluetooth Denied", "Bluetooth access is required for this app");
            }
            else {
                scanForSensors();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (bleScanner == null) {return;}
        bleScanner.onActivityResult(requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBluetoothEnabled() {
        scanForSensors();
    }

    @Override
    public void onBluetoothDenied() {
        showAlert("Bluetooth Denied", "Bluetooth must be on to connect to sensor");
    }
}

