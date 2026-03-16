package com.github.catvod.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Doh {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;
    @SerializedName("ips")
    private List<String> ips;

    public static List<Doh> get() {
        return new ArrayList<>();
    }

    public static Doh objectFrom(String str) {
        Doh item = new Gson().fromJson(str, Doh.class);
        return item == null ? new Doh() : item;
    }

    public static List<Doh> arrayFrom(JsonElement element) {
        Type listType = new TypeToken<List<Doh>>() {}.getType();
        List<Doh> items = new Gson().fromJson(element, listType);
        return items == null ? new ArrayList<>() : items;
    }

    public Doh name(String value) {
        this.name = value;
        return this;
    }

    public Doh url(String value) {
        this.url = value;
        return this;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public List<String> getIps() {
        return ips == null ? Collections.emptyList() : ips;
    }

    public List<InetAddress> getHosts() {
        try {
            List<InetAddress> list = new ArrayList<>();
            for (String ip : getIps()) {
                list.add(InetAddress.getByName(ip));
            }
            return list.isEmpty() ? null : list;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Doh)) {
            return false;
        }
        Doh it = (Doh) obj;
        return getUrl().equals(it.getUrl());
    }

    @NonNull
    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
