package com.portsps2.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Game {
    public long id;
    public String name = "";
    public String origin = "";      // "ZIP" o "ISO"
    public String fileName = "";    // archivo elegido por el usuario
    public long fileSize = 0;       // tamaño del archivo origen
    public String isoName = "";     // ISO interna (en ZIP) o volumen (ISO directa)
    public long isoSize = 0;        // tamaño real de la ISO interna
    public long addedAt = 0;
    public String sourceUri = "";   // Uri (SAF) del archivo origen, con permiso persistente
    public boolean verified = true; // ISO: firma CD001 verificada; si no, "sin verificar"
    public int playCount = 0;
    public long lastPlayedAt = 0;
    public ArrayList<String> saves = new ArrayList<>();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("origin", origin);
            o.put("fileName", fileName);
            o.put("fileSize", fileSize);
            o.put("isoName", isoName);
            o.put("isoSize", isoSize);
            o.put("addedAt", addedAt);
            o.put("sourceUri", sourceUri);
            o.put("verified", verified);
            o.put("playCount", playCount);
            o.put("lastPlayedAt", lastPlayedAt);
            JSONArray arr = new JSONArray();
            for (String s : saves) arr.put(s);
            o.put("saves", arr);
        } catch (JSONException ignored) { }
        return o;
    }

    public static Game fromJson(JSONObject o) {
        Game g = new Game();
        g.id = o.optLong("id");
        g.name = o.optString("name");
        g.origin = o.optString("origin");
        g.fileName = o.optString("fileName");
        g.fileSize = o.optLong("fileSize");
        g.isoName = o.optString("isoName");
        g.isoSize = o.optLong("isoSize");
        g.addedAt = o.optLong("addedAt");
        g.sourceUri = o.optString("sourceUri", "");
        g.verified = o.optBoolean("verified", true); // datos v1.0 sin el campo: verificados
        g.playCount = o.optInt("playCount");
        g.lastPlayedAt = o.optLong("lastPlayedAt");
        JSONArray arr = o.optJSONArray("saves");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) g.saves.add(arr.optString(i));
        }
        return g;
    }
}
