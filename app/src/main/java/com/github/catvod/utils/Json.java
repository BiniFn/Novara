package com.github.catvod.utils;

import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static JsonElement parse(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Throwable ignored) {
            return new JsonParser().parse(json);
        }
    }

    public static boolean isObj(String text) {
        try {
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            new JSONObject(text);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isArray(String text) {
        try {
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            new JSONArray(text);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isEmpty(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return true;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonNull()) {
            return true;
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().isEmpty();
        }
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().trim().isEmpty();
    }

    public static String safeString(JsonObject obj, String key) {
        try {
            return obj.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static List<String> safeListString(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (!obj.has(key)) {
            return result;
        }
        if (obj.get(key).isJsonObject()) {
            result.add(safeString(obj, key));
        } else {
            for (JsonElement opt : obj.getAsJsonArray(key)) {
                result.add(opt.getAsString());
            }
        }
        return result;
    }

    public static List<JsonElement> safeListElement(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (!obj.has(key)) {
            return result;
        }
        if (obj.get(key).isJsonObject()) {
            result.add(obj.get(key).getAsJsonObject());
        }
        for (JsonElement opt : obj.getAsJsonArray(key)) {
            result.add(opt.getAsJsonObject());
        }
        return result;
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) {
                element = parse(element.getAsJsonPrimitive().getAsString());
            }
            return element.getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    public static Map<String, String> toMap(String json) {
        return TextUtils.isEmpty(json) ? null : toMap(parse(json));
    }

    public static Map<String, String> toMap(JsonElement element) {
        Map<String, String> map = new HashMap<>();
        JsonObject object = safeObject(element);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), safeString(object, entry.getKey()));
        }
        return map;
    }
}
