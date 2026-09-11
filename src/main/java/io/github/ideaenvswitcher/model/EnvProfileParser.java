package io.github.ideaenvswitcher.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 解析项目根目录的 env-profiles.json。 */
public final class EnvProfileParser {

    private EnvProfileParser() {
    }

    public static @NotNull List<EnvProfile> parseFile(@NotNull Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static @NotNull List<EnvProfile> parse(@NotNull Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Root element must be a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        JsonArray profiles = object.getAsJsonArray("profiles");
        if (profiles == null) {
            throw new IllegalArgumentException("Missing required field: profiles");
        }

        List<EnvProfile> result = new ArrayList<>();
        for (JsonElement element : profiles) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject profile = element.getAsJsonObject();
            if (!profile.has("name") || profile.get("name").isJsonNull()) {
                throw new IllegalArgumentException("Each profile requires a non-null name");
            }
            String name = profile.get("name").getAsString().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Profile name must not be blank");
            }
            String description = null;
            if (profile.has("description") && !profile.get("description").isJsonNull()) {
                description = profile.get("description").getAsString();
            }
            Map<String, String> env = new LinkedHashMap<>();
            if (profile.has("env") && profile.get("env").isJsonObject()) {
                JsonObject envObject = profile.getAsJsonObject("env");
                for (Map.Entry<String, JsonElement> entry : envObject.entrySet()) {
                    JsonElement value = entry.getValue();
                    env.put(entry.getKey(), value.isJsonNull() ? "" : value.getAsString());
                }
            }
            result.add(new EnvProfile(name, description, env));
        }
        return result;
    }
}
