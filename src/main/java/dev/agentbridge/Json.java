package dev.agentbridge;

import com.google.gson.*;

final class Json {
    static final Gson GSON = new Gson();
    static JsonObject obj(Object... fields) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) result.add((String) fields[i], GSON.toJsonTree(fields[i + 1]));
        return result;
    }
    static JsonArray arr(Object... values) {
        JsonArray a = new JsonArray();
        for (Object v : values) a.add(GSON.toJsonTree(v));
        return a;
    }
    static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
    static JsonObject child(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject();
    }
}
