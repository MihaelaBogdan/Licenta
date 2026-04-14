package com.cityscape.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.cityscape.app.model.Place;
import com.cityscape.app.ui.ar.AROverlayView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ARExplorerActivity extends AppCompatActivity implements SensorEventListener {

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private AROverlayView arOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private List<Place> nearbyPlaces = new ArrayList<>();
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_explorer);

        arOverlay = findViewById(R.id.ar_overlay);
        textureView = findViewById(R.id.camera_preview);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            setupCamera();
        }

        fetchLocationAndPlaces();
    }

    private void setupCamera() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return false; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }
                @Override public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); }
                @Override public void onError(@NonNull CameraDevice camera, int error) { camera.close(); }
            }, null);
        } catch (Exception e) {
            Log.e("AR", "Camera open error", e);
        }
    }

    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            Surface surface = new Surface(texture);
            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(builder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchLocationAndPlaces() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                lastLocation = location;
                loadNearbyPlaces(location.getLatitude(), location.getLongitude());
            }
        });
    }

    private void loadNearbyPlaces(double lat, double lng) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getNearby(lat, lng, "all").enqueue(new Callback<List<Place>>() {
            @Override
            public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().size() > 0) {
                    nearbyPlaces = response.body();
                } else {
                    // DEMO MODE: If no real places nearby, create some virtual ones for testing
                    nearbyPlaces = new ArrayList<>();
                    nearbyPlaces.add(createMockPlace("🏰 Castelul Magic", lat + 0.001, lng + 0.001));
                    nearbyPlaces.add(createMockPlace("☕ Cafeneaua AR", lat - 0.001, lng + 0.002));
                    nearbyPlaces.add(createMockPlace("🌳 Parcul Demo", lat + 0.002, lng - 0.001));
                    nearbyPlaces.add(createMockPlace("🏙️ Zgârie-nori Test", lat - 0.002, lng - 0.002));
                }
                updateAR(0);
            }

            @Override
            public void onFailure(Call<List<Place>> call, Throwable t) {
                // Fallback to mock even on failure for demo purposes
                nearbyPlaces = new ArrayList<>();
                nearbyPlaces.add(createMockPlace("🏰 Castelul Magic", lat + 0.001, lng + 0.001));
                nearbyPlaces.add(createMockPlace("☕ Cafeneaua AR", lat - 0.001, lng + 0.002));
                updateAR(0);
            }
        });
    }

    private Place createMockPlace(String name, double lat, double lng) {
        Place p = new Place();
        p.id = "mock_" + name;
        p.name = name;
        p.latitude = lat;
        p.longitude = lng;
        p.type = "Demo";
        return p;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            float azimuth = event.values[0];
            updateAR(azimuth);
        }
    }

    private void updateAR(float azimuth) {
        if (lastLocation == null) return;
        arOverlay.updateData(nearbyPlaces, azimuth);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        } else {
            Toast.makeText(this, "Camera is required for AR", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
