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

    public static List<ClosetItem> load(Context context) {
        List<ClosetItem> items = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(new ClosetItem(
                        object.optString("name"),
                        object.optString("category"),
                        object.optString("color"),
                        object.optString("season"),
                        object.optString("note"),
                        object.optString("imagePath")
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
                object.put("category", item.category);
                object.put("color", item.color);
                object.put("season", item.season);
                object.put("note", item.note);
                object.put("imagePath", item.imagePath);
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
