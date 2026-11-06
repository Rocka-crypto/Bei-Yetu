package com.beiyetu.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;

import java.util.Date;

/** Firestore: currentPrices/{marketId_cropId}  (one document per market and crop) */
public class CurrentPrice {
    @DocumentId
    public String id;
    public String marketId;
    public String cropId;
    public String marketName;
    public String county;
    public GeoPoint location;
    public double pricePerKg;
    public int reportCount;
    public Date updatedAt;

    public CurrentPrice() { }
}