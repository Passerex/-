package com.example.myapplication.ui;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.model.Trajectory;
import com.example.myapplication.model.TrajectoryPoint;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayFragment extends Fragment {

    private TextView tvStatus;
    private MaterialButton btnPlayPause, btnStop;
    private AppCompatSeekBar seekBar;
    private TextView tvTime;
    private LinearLayout layoutSeek;

    private LocationManager locationManager;
    private Handler playbackHandler;
    private Trajectory playingTrajectory;
    private int currentPointIndex = 0;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private boolean playbackStarted = false;
    private static final String MOCK_PROVIDER = "gps";

    // 共享地图
    private MapView sharedMap;
    private Marker playbackMarker;
    private Polyline trajectoryPolyline;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_play, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvStatus = view.findViewById(R.id.tv_play_status);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnStop = view.findViewById(R.id.btn_stop);
        seekBar = view.findViewById(R.id.seek_playback);
        tvTime = view.findViewById(R.id.tv_time);
        layoutSeek = view.findViewById(R.id.layout_seek);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            sharedMap = activity.getSharedMap();
        }

        locationManager = (LocationManager) requireContext().getSystemService(requireContext().LOCATION_SERVICE);
        playbackHandler = new Handler(Looper.getMainLooper());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnStop.setOnClickListener(v -> stopPlayback());
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次切换到播放标签页时检查是否有待播放轨迹
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            Trajectory pending = activity.getPendingPlayback();
            if (pending != null) {
                startPlayback(pending);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPlayback();
    }

    // ============ GPX 轨迹回放 ============

    public void startPlayback(Trajectory trajectory) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        // 如果 points 为空（loadAll 时未加载完整），重新从 GPX 文件加载
        if (trajectory.points.isEmpty()) {
            try {
                trajectory = activity.getTrajectoryRepository().load(trajectory.name);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "加载 GPX 文件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (trajectory.points.isEmpty()) {
            Toast.makeText(requireContext(), "轨迹没有数据点", Toast.LENGTH_SHORT).show();
            return;
        }

        isPlaying = true;
        isPaused = false;
        playbackStarted = true;
        playingTrajectory = trajectory;
        currentPointIndex = 0;

        activity.setNowPlaying(trajectory.name);

        btnPlayPause.setVisibility(View.VISIBLE);
        btnPlayPause.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause));
        btnStop.setVisibility(View.VISIBLE);
        layoutSeek.setVisibility(View.VISIBLE);
        seekBar.setProgress(0);
        tvTime.setText("00:00 / " + trajectory.getFormattedDuration());

        setupMockProvider();
        drawTrajectoryOnMap(trajectory);

        tvStatus.setText(String.format(Locale.getDefault(), getString(R.string.play_playing),
                trajectory.name, 0, trajectory.getPointCount()));
        scheduleNextPoint();
    }

    private void setupMockProvider() {
        try {
            try {
                locationManager.removeTestProvider(MOCK_PROVIDER);
            } catch (Exception ignored) {}

            locationManager.addTestProvider(
                    MOCK_PROVIDER,
                    false, false, false, false,
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    android.location.Criteria.POWER_HIGH,
                    android.location.Criteria.ACCURACY_FINE
            );
            locationManager.setTestProviderEnabled(MOCK_PROVIDER, true);
        } catch (SecurityException e) {
            Toast.makeText(requireContext(),
                    "需要 ACCESS_MOCK_LOCATION 权限。\n请到「开发者选项 → 选择模拟位置信息应用」中设置本应用。",
                    Toast.LENGTH_LONG).show();
            isPlaying = false;
        } catch (Exception ignored) {
        }
    }

    private void scheduleNextPoint() {
        if (isPaused) {
            // 暂停时持续注入当前位置，防止被系统真实 GPS 覆盖
            if (currentPointIndex > 0 && currentPointIndex <= playingTrajectory.points.size()) {
                injectMockLocation(playingTrajectory.points.get(currentPointIndex - 1));
            }
            playbackHandler.postDelayed(this::scheduleNextPoint, 1000);
            return;
        }
        if (!isPlaying || currentPointIndex >= playingTrajectory.points.size()) {
            playbackComplete();
            return;
        }

        TrajectoryPoint point = playingTrajectory.points.get(currentPointIndex);

        injectMockLocation(point);
        updatePlaybackMarker(point);

        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            act.updatePlayingProgress(currentPointIndex + 1, playingTrajectory.getPointCount());
        }

        // 更新进度条和时间
        int pct = (currentPointIndex + 1) * 100 / playingTrajectory.getPointCount();
        seekBar.setProgress(pct);
        long elapsed = point.timestamp - playingTrajectory.points.get(0).timestamp;
        String elapsedStr = String.format("%02d:%02d", elapsed / 60000, (elapsed / 1000) % 60);
        tvTime.setText(elapsedStr + " / " + playingTrajectory.getFormattedDuration());

        tvStatus.setText(String.format(Locale.getDefault(), getString(R.string.play_playing),
                playingTrajectory.name,
                currentPointIndex + 1,
                playingTrajectory.getPointCount()));

        currentPointIndex++;

        long delay = 1000;
        if (currentPointIndex < playingTrajectory.points.size()) {
            TrajectoryPoint next = playingTrajectory.points.get(currentPointIndex);
            long actualDelay = next.timestamp - point.timestamp;
            if (actualDelay > 100 && actualDelay < 5000) {
                delay = actualDelay;
            }
        }

        playbackHandler.postDelayed(this::scheduleNextPoint, delay);
    }

    private void injectMockLocation(TrajectoryPoint point) {
        Location mockLocation = new Location(MOCK_PROVIDER);
        mockLocation.setLatitude(point.latitude);
        mockLocation.setLongitude(point.longitude);
        mockLocation.setAltitude(point.altitude);
        mockLocation.setSpeed(point.speed);
        mockLocation.setBearing(point.bearing);
        mockLocation.setTime(System.currentTimeMillis());
        mockLocation.setAccuracy(5.0f);
        mockLocation.setElapsedRealtimeNanos(System.nanoTime());

        try {
            locationManager.setTestProviderLocation(MOCK_PROVIDER, mockLocation);
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    // ============ 地图绘制 ============

    private void drawTrajectoryOnMap(Trajectory trajectory) {
        sharedMap.getOverlays().clear();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) activity.restoreMyLocationOverlay();
        playbackMarker = null;
        trajectoryPolyline = null;

        if (trajectory.points.size() < 2) return;

        List<GeoPoint> geoPoints = new ArrayList<>();
        for (TrajectoryPoint p : trajectory.points) {
            geoPoints.add(new GeoPoint(p.latitude, p.longitude));
        }

        trajectoryPolyline = new Polyline();
        trajectoryPolyline.setPoints(geoPoints);
        trajectoryPolyline.setWidth(5f);
        trajectoryPolyline.setColor(0xFF2196F3);
        trajectoryPolyline.setGeodesic(true);
        sharedMap.getOverlays().add(trajectoryPolyline);

        playbackMarker = new Marker(sharedMap);
        playbackMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        playbackMarker.setTitle("当前位置");
        playbackMarker.setPosition(geoPoints.get(0));
        sharedMap.getOverlays().add(playbackMarker);

        sharedMap.zoomToBoundingBox(
                org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints), true, 80);
    }

    private void updatePlaybackMarker(TrajectoryPoint point) {
        if (playbackMarker != null) {
            GeoPoint geo = new GeoPoint(point.latitude, point.longitude);
            playbackMarker.setPosition(geo);
            sharedMap.getController().animateTo(geo);
            sharedMap.invalidate();
        }
    }

    private void togglePlayPause() {
        if (!playbackStarted) return;
        // 播放完成后点击▶️则从头重新播放
        if (!isPlaying && currentPointIndex >= playingTrajectory.points.size()) {
            isPlaying = true;
            currentPointIndex = 0;
            seekBar.setProgress(0);
            btnPlayPause.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause));
            tvStatus.setText(String.format(Locale.getDefault(), getString(R.string.play_playing),
                    playingTrajectory.name, 0, playingTrajectory.getPointCount()));
            scheduleNextPoint();
            return;
        }
        isPaused = !isPaused;
        btnPlayPause.setIcon(ContextCompat.getDrawable(requireContext(),
                isPaused ? R.drawable.ic_play : R.drawable.ic_pause));
        if (!isPaused) scheduleNextPoint();
    }

    private void stopPlayback() {
        isPlaying = false;
        playbackStarted = false;
        playingTrajectory = null;
        currentPointIndex = 0;
        playbackHandler.removeCallbacksAndMessages(null);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) activity.setNowPlaying(null);

        btnPlayPause.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);
        layoutSeek.setVisibility(View.GONE);

        try {
            locationManager.removeTestProvider(MOCK_PROVIDER);
        } catch (Exception ignored) {
        }

        tvStatus.setText(R.string.play_idle);
    }

    private void playbackComplete() {
        isPlaying = false;
        tvStatus.setText(R.string.play_completed);

        // 保持控件可见，按钮切换为播放图标以便重新播放
        btnPlayPause.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_play));

        // 保持 mock provider 激活，继续模拟最后停靠点的位置
    }

}
