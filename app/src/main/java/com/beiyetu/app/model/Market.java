package com.beiyetu.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;

/** Firestore: markets/{marketId} */
public class Market {
    @DocumentId
    public String id;
    public String name;
    public String county;
    public GeoPoint location;
    public boolean active = true;

    public Market() { }
}