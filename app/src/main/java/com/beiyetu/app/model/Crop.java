package com.beiyetu.app.model;

import com.google.firebase.firestore.DocumentId;

/** Firestore: crops/{cropId} */
public class Crop {
    @DocumentId
    public String id;
    public String name;
    public String nameSw;
    public String emoji;
    public int sortOrder;
    public boolean active = true;

    public Crop() { }

    public String label() {
        return (emoji == null ? "" : emoji + " ") + name;
    }
}