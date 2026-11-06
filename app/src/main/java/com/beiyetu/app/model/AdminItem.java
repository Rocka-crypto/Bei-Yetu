package com.beiyetu.app.model;

/** One market, farm location or crop as shown in the admin lists. Only the fields for its type are used. */
public class AdminItem {
    public String id;
    public String name;
    public String county;
    public String nameSw;
    public String emoji;
    public double latitude;
    public double longitude;
    public int sortOrder;
    public boolean active = true;
}