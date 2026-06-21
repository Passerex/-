package com.example.myapplication.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.data.TrajectoryRepository;
import com.example.myapplication.model.Trajectory;
import com.example.myapplication.model.TrajectoryPoint;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 100;

    private MaterialButton btnToggle;
    private TextView tvStatus, tvGpsStatus;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Trajectory currentTrajectory;
    private boolean isRecording = false;
    private long lastStatusUpdate = 0;

    // 共享地图
    private MapView sharedMap;
    private Marker currentMarker;
    private Polyline trajectoryPolyline;
    private final List<GeoPoint> pathPoints = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvStatus = view.findViewById(R.id.tv_status);
        tvGpsStatus = view.findViewById(R.id.tv_gps_status);
        btnToggle = view.findViewById(R.id.btn_toggle_record);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            sharedMap = activity.getSharedMap();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        btnToggle.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                checkPermissionAndStart();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopLocationUpdates();
    }

    // ============ 权限 ============

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.request_location_permission)
                    .setMessage(R.string.request_location_permission)
                    .setPositiveButton(R.string.grant, (d, w) ->
                            requestPermissions(new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            }, LOCATION_PERMISSION_REQUEST))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(requireContext(), R.string.request_location_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ============ 录制 ============

    private void startRecording() {
        isRecording = true;
        currentTrajectory = new Trajectory(TrajectoryRepository.generateTrajectoryName());

        pathPoints.clear();
        sharedMap.getOverlays().clear();
        MainActivity act = (MainActivity) getActivity();
        if (act != null) act.restoreMyLocationOverlay();
        currentMarker = null;
        trajectoryPolyline = null;

        btnToggle.setText(R.string.stop_recording);
        btnToggle.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_record));
        tvStatus.setText(String.format(Locale.getDefault(), getString(R.string.recording_active), 0));

        startLocationUpdates();
    }

    private void stopRecording() {
        isRecording = false;
        stopLocationUpdates();

        btnToggle.setText(R.string.start_recording);
        btnToggle.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_record));
        tvStatus.setText(R.string.recording_idle);

        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        if (currentTrajectory != null && currentTrajectory.getPointCount() >= 2) {
            try {
                activity.getTrajectoryRepository().save(currentTrajectory);
                Toast.makeText(requireContext(), R.string.recording_saved, Toast.LENGTH_SHORT).show();
                activity.refreshTrajectoryList();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (currentTrajectory != null) {
            Toast.makeText(requireContext(), "采集点数过少，未保存", Toast.LENGTH_SHORT).show();
        }

        currentTrajectory = null;
    }

    // ============ GPS 更新 ============

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isRecording) return;

                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    currentTrajectory.points.add(new TrajectoryPoint(
                            loc.getLatitude(),
                            loc.getLongitude(),
                            loc.getAltitude(),
                            loc.getSpeed(),
                            loc.getBearing(),
                            System.currentTimeMillis()
                    ));

                    // 更新地图
                    GeoPoint geoPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                    updateMapWithLocation(geoPoint);

                    long now = System.currentTimeMillis();
                    if (now - lastStatusUpdate > 500) {
                        lastStatusUpdate = now;
                        int count = currentTrajectory.getPointCount();
                        tvStatus.setText(String.format(Locale.getDefault(),
                                getString(R.string.recording_active), count));
                        tvGpsStatus.setText(String.format(Locale.getDefault(),
                                "精度: %.1f m  ·  速度: %.1f km/h",
                                loc.getAccuracy(),
                                loc.getSpeed() * 3.6));
                    }
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            tvGpsStatus.setText(R.string.gps_searching);
        } catch (SecurityException e) {
            tvGpsStatus.setText(R.string.gps_lost);
        }
    }

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    // ============ 地图更新 ============

    private void updateMapWithLocation(GeoPoint geoPoint) {
        pathPoints.add(geoPoint);

        if (currentMarker == null) {
            currentMarker = new Marker(sharedMap);
            currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            currentMarker.setTitle("当前位置");
            sharedMap.getOverlays().add(currentMarker);
        }
        currentMarker.setPosition(geoPoint);

        if (pathPoints.size() >= 2) {
            if (trajectoryPolyline == null) {
                trajectoryPolyline = new Polyline();
                trajectoryPolyline.setWidth(5f);
                trajectoryPolyline.setColor(0xFF2196F3);
                trajectoryPolyline.setGeodesic(true);
                sharedMap.getOverlays().add(trajectoryPolyline);
            }
            trajectoryPolyline.setPoints(new ArrayList<>(pathPoints));
        }

        sharedMap.getController().setCenter(geoPoint);
    }
}
