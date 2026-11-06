package com.beiyetu.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;

/** Firestore: origins/{originId}  (farm locations a farmer can pick) */
public class Origin {
    @DocumentId
    public String id;
    public String name;
    public String county;
    public GeoPoint location;

    public Origin() { }
}