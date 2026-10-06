package one.ggsky.alternativeauth.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class AlternativeAuthUtils {
    private AlternativeAuthUtils() {}

    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeHierarchyAdapter(PropertyMap.class, (JsonSerializer<PropertyMap>) (src, type, ctx) -> {
            JsonArray arr = new JsonArray();
            for (Map.Entry<String, Property> entry : src.entries()) {
                JsonObject prop = new JsonObject();

                prop.addProperty("name", entry.getKey());
                prop.addProperty("value", entry.getValue().value());

                String signature = entry.getValue().signature();

                if (signature != null) {
                    prop.addProperty("signature", signature);
                }

                arr.add(prop);
            }
            return arr;
        })
        .create();

    public static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(url + " is a malformed URL", e);
        }
    }

    public static String buildQuery(Map<String, ?> arguments) {
        StringBuilder query = new StringBuilder();

        for (Map.Entry<String, ?> entry : arguments.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }

            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }

        return query.toString();
    }

    public static URL appendQuery(String url, Map<String, ?> arguments) {
        String query = buildQuery(arguments);

        if (query.isEmpty()) {
            return toUrl(url);
        }

        String separator = url.contains("?") ? "&" : "?";
        return toUrl(url + separator + query);
    }
}