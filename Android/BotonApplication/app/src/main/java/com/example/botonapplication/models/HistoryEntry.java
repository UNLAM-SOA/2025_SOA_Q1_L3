package com.example.botonapplication.models;

public class HistoryEntry {
    public final String status;
    public final String timestamp;
    public final double latitude;
    public final double longitude;

    public HistoryEntry(String status, String timestamp, double latitude, double longitude) {
        this.status = status;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
