package com.beiyetu.app.data;

import com.beiyetu.app.data.FirebaseRepo.Result;
import com.beiyetu.app.model.AdminItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything an administrator can do. An admin is a signed-in (non-anonymous) user whose uid
 * has a document in the "admins" collection.
 */
public class AdminRepo {

    /** A report that was held for review. */
    public static class FlaggedItem {
        public String id, marketId, marketName, cropId, cropName, reason, source;
        public double price;
    }

    private static AdminRepo instance;

    public static synchronized AdminRepo get() {
        if (instance == null) instance = new AdminRepo();
        return instance;
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private AdminRepo() { }

    // ------------------------------------------------------------------ sign in
    public String currentEmail() {
        FirebaseUser u = auth.getCurrentUser();
        return (u == null || u.isAnonymous()) ? null : u.getEmail();
    }

    /** Calls back true when the current user is a real account listed in admins/{uid}. */
    public void checkAdmin(Result<Boolean> cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null || u.isAnonymous()) {
            cb.onSuccess(false);
            return;
        }
        db.collection("admins").document(u.getUid()).get()
                .addOnSuccessListener(d -> cb.onSuccess(d.exists()))
                .addOnFailureListener(e -> cb.onSuccess(false));
    }

    public void signIn(String email, String password, Result<Boolean> cb) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> checkAdmin(new Result<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isAdmin) {
                        if (isAdmin) {
                            cb.onSuccess(true);
                        } else {
                            signOut(() -> cb.onError("This account is not an admin. Ask the project owner to add it to the admins collection."));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                }))
                .addOnFailureListener(e -> cb.onError(e.getMessage() == null ? "Sign-in failed." : e.getMessage()));
    }

    /** Signs out of the admin account and goes back to the normal anonymous user. */
    public void signOut(Runnable done) {
        auth.signOut();
        auth.signInAnonymously().addOnCompleteListener(t -> done.run());
    }

    // ------------------------------------------------------------------ markets, farm locations, crops
    /** type = "markets", "origins" or "crops". Includes hidden items. */
    public void listItems(String type, Result<List<AdminItem>> cb) {
        Query q = "crops".equals(type)
                ? db.collection("crops").orderBy("sortOrder")
                : db.collection(type).orderBy("name");
        q.get().addOnSuccessListener(snap -> {
            List<AdminItem> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                AdminItem it = new AdminItem();
                it.id = d.getId();
                it.name = d.getString("name");
                it.county = d.getString("county");
                it.nameSw = d.getString("nameSw");
                it.emoji = d.getString("emoji");
                GeoPoint g = d.getGeoPoint("location");
                if (g != null) {
                    it.latitude = g.getLatitude();
                    it.longitude = g.getLongitude();
                }
                Long so = d.getLong("sortOrder");
                it.sortOrder = so == null ? 0 : so.intValue();
                Boolean a = d.getBoolean("active");
                it.active = a == null || a;
                list.add(it);
            }
            cb.onSuccess(list);
        }).addOnFailureListener(e -> cb.onError(msg(e)));
    }

    /** Adds or updates a market or farm location. id = null means a new item. */
    public void savePlace(String collection, String id, String name, String county,
                          double lat, double lon, boolean active, Result<String> cb) {
        if (name.isEmpty()) {
            cb.onError("Enter a name.");
            return;
        }
        if (lat < -5.5 || lat > 5.5 || lon < 33 || lon > 42.5) {
            cb.onError("Those coordinates are outside Kenya. Check that latitude and longitude are not swapped.");
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("county", county);
        m.put("location", new GeoPoint(lat, lon));
        m.put("active", active);
        String docId = id != null ? id : slug(name);
        db.collection(collection).document(docId).set(m, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onSuccess(name + " saved."))
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    /** Adds or updates a crop. id = null means a new crop. */
    public void saveCrop(String id, String name, String nameSw, String emoji,
                         int sortOrder, boolean active, Result<String> cb) {
        if (name.isEmpty()) {
            cb.onError("Enter the crop name.");
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("nameSw", nameSw);
        m.put("emoji", emoji.isEmpty() ? "🌾" : emoji);
        m.put("sortOrder", sortOrder);
        m.put("active", active);
        String docId = id != null ? id : slug(name);
        db.collection("crops").document(docId).set(m, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onSuccess(name + " saved."))
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    public void deleteItem(String collection, String id, Result<String> cb) {
        db.collection(collection).document(id).delete()
                .addOnSuccessListener(v -> cb.onSuccess("Deleted."))
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    // ------------------------------------------------------------------ settings
    public void loadSettings(Result<Map<String, Object>> cb) {
        db.collection("settings").document("config").get()
                .addOnSuccessListener(d -> {
                    Map<String, Object> m = new HashMap<>();
                    if (d.exists() && d.getData() != null) m.putAll(d.getData());
                    cb.onSuccess(m);
                })
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    public void saveSettings(Map<String, Object> values, Result<String> cb) {
        db.collection("settings").document("config").set(values, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    FirebaseRepo.get().clearConfigCache();
                    cb.onSuccess("Settings saved.");
                })
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    // ------------------------------------------------------------------ flagged reports
    public void listFlagged(Result<List<FlaggedItem>> cb) {
        db.collection("priceReports")
                .orderBy("reportedAt", Query.Direction.DESCENDING).limit(100).get()
                .addOnSuccessListener(snap -> {
                    List<FlaggedItem> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Boolean flagged = d.getBoolean("flagged");
                        if (flagged == null || !flagged || d.getString("reviewed") != null) continue;
                        FlaggedItem f = new FlaggedItem();
                        f.id = d.getId();
                        f.marketId = d.getString("marketId");
                        f.marketName = d.getString("marketName");
                        f.cropId = d.getString("cropId");
                        f.cropName = d.getString("cropName");
                        Double p = d.getDouble("pricePerKg");
                        f.price = p == null ? 0 : p;
                        f.reason = d.getString("flagReason");
                        f.source = d.getString("source");
                        list.add(f);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    /** Approve = count the price (and refresh the headline price). Reject = ignore it. */
    public void review(FlaggedItem f, boolean approve, Result<String> cb) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("reviewed", approve ? "approved" : "rejected");
        upd.put("reviewedAt", FieldValue.serverTimestamp());
        if (approve) upd.put("flagged", false);
        db.collection("priceReports").document(f.id).update(upd)
                .addOnSuccessListener(v -> {
                    if (approve) recompute(f.marketId, f.cropId, cb);
                    else cb.onSuccess("Rejected.");
                })
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    private void recompute(String marketId, String cropId, Result<String> cb) {
        String pair = marketId + "_" + cropId;
        db.collection("settings").document("config").get().addOnCompleteListener(t -> {
            long hours = 24;
            if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) {
                Long h = t.getResult().getLong("reportWindowHours");
                if (h != null) hours = h;
            }
            final Date since = new Date(System.currentTimeMillis() - hours * 3600_000L);
            db.collection("priceReports").whereEqualTo("pairKey", pair)
                    .whereGreaterThan("reportedAt", since).get()
                    .addOnSuccessListener(snap -> {
                        List<Double> prices = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Boolean flagged = d.getBoolean("flagged");
                            Double p = d.getDouble("pricePerKg");
                            if (p != null && (flagged == null || !flagged)) prices.add(p);
                        }
                        if (prices.isEmpty()) {
                            cb.onSuccess("Approved.");
                            return;
                        }
                        final double median = Geo.median(prices);
                        final int count = prices.size();
                        db.collection("markets").document(marketId).get()
                                .addOnSuccessListener(m -> {
                                    Map<String, Object> cp = new HashMap<>();
                                    cp.put("marketId", marketId);
                                    cp.put("cropId", cropId);
                                    cp.put("marketName", m.getString("name"));
                                    cp.put("county", m.getString("county"));
                                    GeoPoint loc = m.getGeoPoint("location");
                                    if (loc != null) cp.put("location", loc);
                                    cp.put("pricePerKg", median);
                                    cp.put("reportCount", count);
                                    cp.put("updatedAt", FieldValue.serverTimestamp());
                                    db.collection("currentPrices").document(pair).set(cp, SetOptions.merge())
                                            .addOnSuccessListener(v -> cb.onSuccess("Approved. Price updated."))
                                            .addOnFailureListener(e -> cb.onError(msg(e)));
                                })
                                .addOnFailureListener(e -> cb.onError(msg(e)));
                    })
                    .addOnFailureListener(e -> cb.onError(msg(e)));
        });
    }

    // ------------------------------------------------------------------ helpers
    private static String msg(Exception e) {
        String m = e.getMessage() == null ? "Unknown error" : e.getMessage();
        if (m.contains("PERMISSION_DENIED")) {
            return "Only admins can do this. Check you are signed in as an admin and that the latest rules are published.";
        }
        if (m.contains("requires an index")) {
            return "A database index is missing. Open Logcat, tap the link in the error and create it.";
        }
        return m;
    }

    static String slug(String s) {
        String x = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return x.isEmpty() ? "item" : x;
    }
}