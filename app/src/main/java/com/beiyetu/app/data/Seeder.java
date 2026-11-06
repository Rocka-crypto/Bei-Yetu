package com.beiyetu.app.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Fills an empty Firestore database with demo data (one atomic batch, 74 writes).
 * It runs once, the first time the app starts and finds no settings/config document.
 */
final class Seeder {
    private Seeder() { }

    static Task<Void> seed(FirebaseFirestore db, String uid) {
        WriteBatch b = db.batch();

        // ---- crops ----
        String[][] crops = {
                {"tomatoes", "Tomatoes", "Nyanya", "🍅"},
                {"potatoes", "Potatoes", "Viazi", "🥔"},
                {"cabbages", "Cabbages", "Kabichi", "🥬"}
        };
        for (int i = 0; i < crops.length; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", crops[i][1]);
            m.put("nameSw", crops[i][2]);
            m.put("emoji", crops[i][3]);
            m.put("sortOrder", i);
            m.put("active", true);
            b.set(db.collection("crops").document(crops[i][0]), m);
        }

        // ---- markets ----
        Object[][] markets = {
                {"wakulima-nairobi", "Wakulima Nairobi", "Nairobi", -1.2780, 36.8270},
                {"kibuye-kisumu", "Kibuye Kisumu", "Kisumu", -0.1022, 34.7500},
                {"wakulima-nakuru", "Wakulima Nakuru", "Nakuru", -0.2900, 36.0600},
                {"eldoret", "Eldoret Market", "Uasin Gishu", 0.5143, 35.2698},
                {"kongowea-mombasa", "Kongowea Mombasa", "Mombasa", -4.0100, 39.6700}
        };
        for (Object[] mk : markets) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", mk[1]);
            m.put("county", mk[2]);
            m.put("location", new GeoPoint((Double) mk[3], (Double) mk[4]));
            m.put("active", true);
            b.set(db.collection("markets").document((String) mk[0]), m);
        }

        // ---- origins (farm locations) ----
        Object[][] origins = {
                {"ol-kalou", "Ol Kalou (Nyandarua)", "Nyandarua", -0.2700, 36.3800},
                {"kerugoya", "Kerugoya (Kirinyaga)", "Kirinyaga", -0.5000, 37.2800},
                {"nakuru", "Nakuru", "Nakuru", -0.3031, 36.0800},
                {"kitale", "Kitale (Trans Nzoia)", "Trans Nzoia", 1.0157, 35.0062},
                {"bungoma", "Bungoma", "Bungoma", 0.5635, 34.5606}
        };
        for (Object[] o : origins) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", o[1]);
            m.put("county", o[2]);
            m.put("location", new GeoPoint((Double) o[3], (Double) o[4]));
            b.set(db.collection("origins").document((String) o[0]), m);
        }

        // ---- settings ----
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("transportCostPerKgKm", 0.04);
        cfg.put("reportWindowHours", 24);
        cfg.put("outlierLow", 0.5);
        cfg.put("outlierHigh", 1.5);
        cfg.put("minReportsForVerification", 3);
        b.set(db.collection("settings").document("config"), cfg);

        // ---- sample prices: base price per kg, same order as the markets above ----
        double[][] base = {
                {80, 95, 65, 70, 100},   // tomatoes
                {45, 50, 38, 40, 55},    // potatoes
                {30, 35, 25, 28, 40}     // cabbages
        };
        double[] variation = {-2, 0, 2};

        for (int c = 0; c < crops.length; c++) {
            for (int m = 0; m < markets.length; m++) {
                String cropId = crops[c][0];
                String marketId = (String) markets[m][0];
                String pair = marketId + "_" + cropId;

                for (double v : variation) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("marketId", marketId);
                    r.put("marketName", markets[m][1]);
                    r.put("cropId", cropId);
                    r.put("cropName", crops[c][1]);
                    r.put("pairKey", pair);
                    r.put("pricePerKg", base[c][m] + v);
                    r.put("reporterUid", uid);
                    r.put("reportedAt", FieldValue.serverTimestamp());
                    r.put("flagged", false);
                    r.put("source", "seed");
                    b.set(db.collection("priceReports").document(), r);
                }

                Map<String, Object> cp = new HashMap<>();
                cp.put("marketId", marketId);
                cp.put("cropId", cropId);
                cp.put("marketName", markets[m][1]);
                cp.put("county", markets[m][2]);
                cp.put("location", new GeoPoint((Double) markets[m][3], (Double) markets[m][4]));
                cp.put("pricePerKg", base[c][m]);
                cp.put("reportCount", variation.length);
                cp.put("updatedAt", FieldValue.serverTimestamp());
                b.set(db.collection("currentPrices").document(pair), cp);
            }
        }
        return b.commit();
    }
}