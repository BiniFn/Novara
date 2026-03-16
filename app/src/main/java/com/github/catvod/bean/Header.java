package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Header {

    @SerializedName("host")
    private String host;
    @SerializedName("header")
    private JsonElement header;

    public static List<Header> arrayFrom(JsonElement element) {
        try {
            Type listType = new TypeToken<List<Header>>() {}.getType();
            List<Header> items = new Gson().fromJson(element, listType);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public JsonElement getHeader() {
        return header;
    }
}
