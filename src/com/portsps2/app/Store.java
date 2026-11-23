package com.portsps2.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Persistencia simple de la biblioteca en SharedPreferences (JSON). */
public class Store {
    private static final String PREFS = "ports_ps2";
    private static final String KEY = "games";

    public static ArrayList<Game> load(Context ctx) {
        ArrayList<Game> out = new ArrayList<>();
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(Game.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) { }
        return out;
    }

    public static void save(Context ctx, ArrayList<Game> games) {
        JSONArray arr = new JSONArray();
        for (Game g : games) arr.put(g.toJson());
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, arr.toString());
        e.apply();
    }

    public static Game find(Context ctx, long id) {
        for (Game g : load(ctx)) if (g.id == id) return g;
        return null;
    }

    public static void upsert(Context ctx, Game game) {
        ArrayList<Game> games = load(ctx);
        boolean found = false;
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).id == game.id) { games.set(i, game); found = true; break; }
        }
        if (!found) games.add(game);
        save(ctx, games);
    }

    public static void remove(Context ctx, long id) {
        ArrayList<Game> games = load(ctx);
        ArrayList<Game> keep = new ArrayList<>();
        for (Game g : games) if (g.id != id) keep.add(g);
        save(ctx, keep);
    }

    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "tamaño desconocido";
        String[] u = {"B", "KB", "MB", "GB"};
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    public static String fmtDate(long ms) {
        if (ms <= 0) return "nunca";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(ms));
    }
}
