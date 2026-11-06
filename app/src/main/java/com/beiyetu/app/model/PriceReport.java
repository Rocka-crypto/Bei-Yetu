package com.beiyetu.app.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

/** Firestore: priceReports/{reportId}  (every report ever submitted) */
public class PriceReport {
    @DocumentId
    public String id;
    public String marketId;
    public String marketName;
    public String cropId;
    public String cropName;
    public String pairKey;
    public double pricePerKg;
    public String reporterUid;
    public Date reportedAt;
    public boolean flagged;
    public String flagReason;
    public String source;   // "app", "ussd" or "seed"

    public PriceReport() { }
}