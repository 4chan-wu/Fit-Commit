package com.fitcommit.app.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.fitcommit.app.models.ClosetItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ClothingStorage {
    private static final String PREF_NAME = "fitcommit_closet";
    private static final String KEY_ITEMS = "items";

    private ClothingStorage() {
    }

    public static void save(Context context, ClosetItem item) {
        List<ClosetItem> items = load(context);
        items.add(0, item);
        persist(context, items);
    }

    public static void saveOrReplace(Context context, ClosetItem item) {
        List<ClosetItem> items = load(context);
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).localId.equals(item.localId)) {
                items.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            items.add(0, item);
        }
        persist(context, items);
    }

    public static ClosetItem findByLocalId(Context context, String localId) {
        for (ClosetItem item : load(context)) {
            if (item.localId.equals(localId)) {
                return item;
            }
        }
        return null;
    }

    public static void updateSync(Context context, String localId, String remoteId, String syncStatus, String syncError) {
        ClosetItem item = findByLocalId(context, localId);
        if (item == null) {
            return;
        }
        saveOrReplace(context, new ClosetItem(
                item.localId,
                remoteId == null ? item.remoteId : remoteId,
                item.name,
                item.category,
                item.color,
                item.season,
                item.note,
                item.imagePath,
                item.analysisStatus,
                item.analysisError,
                item.needsAnalysis,
                syncStatus,
                syncError,
                item.createdAt
        ));
    }

    public static void updateAnalysis(Context context, String localId, ClosetItem updatedItem) {
        saveOrReplace(context, updatedItem);
    }

    public static List<ClosetItem> load(Context context) {
        List<ClosetItem> items = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(new ClosetItem(
                        object.optString("localId"),
                        object.optString("remoteId"),
                        object.optString("name"),
                        object.optString("category"),
                        object.optString("color"),
                        object.optString("season"),
                        object.optString("note"),
                        object.optString("imagePath"),
                        object.optString("analysisStatus", "completed"),
                        object.optString("analysisError", ""),
                        object.optBoolean("needsAnalysis", false),
                        object.optString("syncStatus", "local_only"),
                        object.optString("syncError", ""),
                        object.optLong("createdAt", System.currentTimeMillis())
                ));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private static void persist(Context context, List<ClosetItem> items) {
        JSONArray array = new JSONArray();
        for (ClosetItem item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", item.name);
                object.put("localId", item.localId);
                object.put("remoteId", item.remoteId);
                object.put("category", item.category);
                object.put("color", item.color);
                object.put("season", item.season);
                object.put("note", item.note);
                object.put("imagePath", item.imagePath);
                object.put("analysisStatus", item.analysisStatus);
                object.put("analysisError", item.analysisError);
                object.put("needsAnalysis", item.needsAnalysis);
                object.put("syncStatus", item.syncStatus);
                object.put("syncError", item.syncError);
                object.put("createdAt", item.createdAt);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, array.toString())
                .apply();
    }
}
