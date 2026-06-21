package com.example.myapplication;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.data.TrajectoryAdapter;
import com.example.myapplication.data.TrajectoryRepository;
import com.example.myapplication.ui.PlayFragment;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.model.Trajectory;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TrajectoryRepository repository;
    private TrajectoryAdapter adapter;
    private List<Trajectory> trajectoryList;
    private MapView sharedMap;
    private MyLocationNewOverlay myLocationOverlay;
    private Trajectory pendingPlayback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化共享地图
        sharedMap = binding.sharedMap;
        Configuration.getInstance().setUserAgentValue(getPackageName());
        sharedMap.setTileSource(TileSourceFactory.MAPNIK);
        sharedMap.setMultiTouchControls(true);
        sharedMap.getController().setZoom(17.0);
        sharedMap.getController().setCenter(new GeoPoint(39.9042, 116.4074));

        // 添加实时位置蓝点（始终显示当前位置）
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), sharedMap);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        sharedMap.getOverlays().add(0, myLocationOverlay);

        // 设置底部导航栏
        BottomNavigationView navView = binding.bottomNavigation;
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavigationUI.setupWithNavController(navView, navHostFragment.getNavController());
        }

        // 初始化共享轨迹列表
        repository = new TrajectoryRepository(this);
        RecyclerView rv = binding.rvTrajectories;
        trajectoryList = repository.loadAll();
        adapter = new TrajectoryAdapter(trajectoryList, new TrajectoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Trajectory t) {
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(t.name)
                        .setMessage(String.format(Locale.getDefault(),
                                "时长: %s\n距离: %s\n点数: %d",
                                t.getFormattedDuration(),
                                t.getFormattedDistance(),
                                t.getPointCount()))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onPlayClick(Trajectory trajectory) {
                pendingPlayback = trajectory;
                binding.bottomNavigation.setSelectedItemId(R.id.navigation_play);
                // 如果已在播放标签页，onResume 不会触发，需要直接调用
                PlayFragment playFrag = findPlayFragment();
                if (playFrag != null) {
                    playFrag.startPlayback(trajectory);
                    pendingPlayback = null;
                }
            }

            @Override
            public void onRenameClick(Trajectory t) {
                showRenameDialog(t);
            }

            @Override
            public void onDeleteClick(Trajectory t) {
                showDeleteDialog(t);
            }
        }, true);
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sharedMap != null) {
            sharedMap.onResume();
            if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        if (sharedMap != null) sharedMap.onPause();
    }

    /** 设置当前播放状态，更新列表指示器 */
    public void setNowPlaying(String trajectoryName) {
        adapter.setNowPlaying(trajectoryName);
    }

    /** 更新播放进度，显示在轨迹卡片上 */
    public void updatePlayingProgress(int current, int total) {
        adapter.updatePlayingProgress(current, total);
    }

    /** 刷新轨迹列表（供 Fragment 调用） */
    public void refreshTrajectoryList() {
        List<Trajectory> newList = repository.loadAll();
        trajectoryList.clear();
        trajectoryList.addAll(newList);
        adapter.notifyDataSetChanged();
    }

    public TrajectoryRepository getTrajectoryRepository() {
        return repository;
    }

    /** 恢复实时位置蓝点（Fragment 清除地图后调用） */
    public void restoreMyLocationOverlay() {
        if (myLocationOverlay != null && !sharedMap.getOverlays().contains(myLocationOverlay)) {
            sharedMap.getOverlays().add(0, myLocationOverlay);
            myLocationOverlay.enableMyLocation();
        }
    }

    public MapView getSharedMap() {
        return sharedMap;
    }

    /** 获取待播放轨迹（PlayFragment 调用后自动清除） */
    public Trajectory getPendingPlayback() {
        Trajectory t = pendingPlayback;
        pendingPlayback = null;
        return t;
    }

    /** 查找当前的 PlayFragment（如果正在播放标签页） */
    private PlayFragment findPlayFragment() {
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            androidx.fragment.app.Fragment f = navHost.getChildFragmentManager().getFragments().get(0);
            if (f instanceof PlayFragment) return (PlayFragment) f;
        }
        return null;
    }

    private void showRenameDialog(Trajectory t) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("重命名轨迹");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(t.name);
        input.setSelection(t.name.length());
        input.setHint("输入新名称");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) return;
            if (repository.rename(t.name, newName)) {
                refreshTrajectoryList();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDeleteDialog(Trajectory t) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确定删除轨迹「" + t.name + "」？")
                .setPositiveButton("确定", (d, w) -> {
                    repository.delete(t.name);
                    refreshTrajectoryList();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}