package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条完整的运动轨迹
 */
public class Trajectory {
    public String name;                // 文件名（不含后缀）
    public long startTime;             // 开始时间戳（毫秒）
    public long endTime;               // 结束时间戳（毫秒）
    public List<TrajectoryPoint> points;

    public Trajectory() {
        points = new ArrayList<>();
    }

    public Trajectory(String name) {
        this.name = name;
        this.startTime = System.currentTimeMillis();
        this.points = new ArrayList<>();
    }

    /** 轨迹持续时长（毫秒） */
    public long getDuration() {
        if (endTime > startTime) {
            return endTime - startTime;
        }
        return 0;
    }

    /** 总距离（米），基于经纬度粗略计算 */
    public double getTotalDistance() {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += distanceBetween(points.get(i - 1), points.get(i));
        }
        return total;
    }

    public int getPointCount() {
        return points.size();
    }

    /** 格式化时长 MM:SS */
    public String getFormattedDuration() {
        long secs = getDuration() / 1000;
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    /** 格式化距离 */
    public String getFormattedDistance() {
        double meters = getTotalDistance();
        if (meters >= 1000) {
            return String.format("%.2f km", meters / 1000);
        }
        return String.format("%.0f m", meters);
    }

    /** Haversine 公式计算两点间距离（米） */
    private static double distanceBetween(TrajectoryPoint p1, TrajectoryPoint p2) {
        double R = 6371000;
        double dLat = Math.toRadians(p2.latitude - p1.latitude);
        double dLng = Math.toRadians(p2.longitude - p1.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
