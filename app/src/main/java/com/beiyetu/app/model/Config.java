package com.beiyetu.app.model;

/** Firestore: settings/config. Field defaults are used if a field is missing in the document. */
public class Config {
    public double transportCostPerKgKm = 0.04;
    public int reportWindowHours = 24;
    public double outlierLow = 0.5;
    public double outlierHigh = 1.5;
    public int minReportsForVerification = 3;
    public double glutAcresThreshold = 50;

    public Config() { }
}