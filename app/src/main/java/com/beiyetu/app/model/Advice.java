package com.beiyetu.app.model;

/** Result of the "where should I sell?" calculation (not stored in Firestore). */
public class Advice {
    public final String marketName;
    public final String county;
    public final double pricePerKg;
    public final double distanceKm;
    public final double transportCost;
    public final double grossIncome;
    public final double netIncome;

    public Advice(String marketName, String county, double pricePerKg, double distanceKm,
                  double transportCost, double grossIncome, double netIncome) {
        this.marketName = marketName;
        this.county = county;
        this.pricePerKg = pricePerKg;
        this.distanceKm = distanceKm;
        this.transportCost = transportCost;
        this.grossIncome = grossIncome;
        this.netIncome = netIncome;
    }
}