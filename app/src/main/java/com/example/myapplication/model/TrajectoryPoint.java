package com.example.myapplication.model;

/**
 * 轨迹中的一个采样点
 */
public class TrajectoryPoint {
    public double latitude;
    public double longitude;
    public double altitude;
    public float speed;       // 米/秒
    public float bearing;     // 方向角
    public long timestamp;    // 毫秒时间戳

    public TrajectoryPoint() {
    }

    public TrajectoryPoint(double latitude, double longitude, double altitude,
                           float speed, float bearing, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.bearing = bearing;
        this.timestamp = timestamp;
    }
}
