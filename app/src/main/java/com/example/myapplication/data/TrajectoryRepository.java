package com.example.myapplication.data;

import android.content.Context;

import com.example.myapplication.model.Trajectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 轨迹文件的存储与读取（GPX 格式）
 */
public class TrajectoryRepository {

    private static final String TRAJECTORIES_DIR = "trajectories";

    private final File storageDir;

    public TrajectoryRepository(Context context) {
        storageDir = new File(context.getFilesDir(), TRAJECTORIES_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    /** 生成轨迹文件名（基于时间戳） */
    public static String generateTrajectoryName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return "trajectory_" + sdf.format(new Date());
    }

    /** 保存轨迹为 GPX 文件 */
    public void save(Trajectory trajectory) throws Exception {
        trajectory.endTime = System.currentTimeMillis();
        File file = new File(storageDir, trajectory.name + ".gpx");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            GpxParser.write(trajectory, fos);
        }
    }

    /** 加载所有轨迹（仅元数据，不包含完整 points） */
    public List<Trajectory> loadAll() {
        List<Trajectory> list = new ArrayList<>();
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".gpx"));
        if (files == null) return list;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        for (File file : files) {
            try {
                Trajectory t = loadFromFile(file);
                list.add(t);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /** 加载单个轨迹（含完整 points） */
    public Trajectory load(String name) throws Exception {
        File file = new File(storageDir, name + ".gpx");
        return loadFromFile(file);
    }

    /** 删除轨迹文件 */
    public boolean delete(String name) {
        File file = new File(storageDir, name + ".gpx");
        return file.delete();
    }

    /** 重命名轨迹文件 */
    public boolean rename(String oldName, String newName) {
        if (oldName.equals(newName)) return true;
        File oldFile = new File(storageDir, oldName + ".gpx");
        File newFile = new File(storageDir, newName + ".gpx");
        if (newFile.exists()) return false;
        return oldFile.renameTo(newFile);
    }

    private Trajectory loadFromFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            Trajectory t = GpxParser.read(fis);
            // 使用文件名（不含 .gpx 后缀）作为轨迹名称
            String name = file.getName();
            if (name.endsWith(".gpx")) {
                name = name.substring(0, name.length() - 4);
            }
            t.name = name;
            return t;
        }
    }
}
