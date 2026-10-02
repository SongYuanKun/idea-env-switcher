package io.github.ideaenvswitcher.model;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Staged settings documents, validation and conflict-aware atomic persistence. */
public final class EnvProfileStore {
    private EnvProfileStore() { }

    public static final class Snapshot {
        private final byte @Nullable [] content;
        private final List<EnvProfile> profiles;

        private Snapshot(byte @Nullable [] content, List<EnvProfile> profiles) {
            this.content = content == null ? null : content.clone();
            this.profiles = List.copyOf(profiles);
        }

        public @NotNull List<EnvProfile> profiles() { return profiles; }
    }

    public static @NotNull Snapshot load(@NotNull Path file) throws IOException {
        byte[] content = read(file);
        if (content == null) return new Snapshot(null, List.of());
        try {
            List<EnvProfile> profiles = EnvProfileParser.parse(new StringReader(new String(content, StandardCharsets.UTF_8)));
            validate(profiles);
            return new Snapshot(content, profiles);
        } catch (RuntimeException e) {
            // JSON parser diagnostics can contain environment values; never expose them.
            throw new IOException("Invalid env-profiles.json. Correct the file and reload settings.");
        }
    }

    public static @NotNull Snapshot save(@NotNull Path file, @NotNull Snapshot expected,
                                         @NotNull List<EnvProfile> profiles) throws IOException {
        validate(profiles);
        if (!Arrays.equals(expected.content, read(file))) {
            throw new IOException("env-profiles.json changed outside settings. Reset settings and try again.");
        }
        byte[] content = toJson(profiles).getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(file.getParent(), ".env-profiles-", ".tmp");
        try {
            Files.write(temporary, content);
            // Check again after preparing the replacement to narrow the conflict window.
            if (!Arrays.equals(expected.content, read(file))) {
                throw new IOException("env-profiles.json changed outside settings. Reset settings and try again.");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Snapshot(content, profiles);
    }

    private static byte @Nullable [] read(Path file) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException("env-profiles.json must be a regular file, not a symbolic link.");
        return Files.exists(file) ? Files.readAllBytes(file) : null;
    }

    public static void validate(@NotNull List<EnvProfile> profiles) {
        Set<String> names = new HashSet<>();
        for (EnvProfile profile : profiles) {
            String name = profile.getName();
            if (name.isBlank() || !name.equals(name.trim()) || name.contains("\n") || name.contains("\r") || name.contains("\0")) {
                throw new IllegalArgumentException("Profile names must be non-empty, single-line names without surrounding whitespace.");
            }
            if (!names.add(name)) throw new IllegalArgumentException("Profile names must be unique.");
            for (var entry : profile.getEnv().entrySet()) {
                if (!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("Variable names must contain letters, digits or underscores and cannot start with a digit.");
                }
                if (entry.getValue().contains("\0")) throw new IllegalArgumentException("Variable values cannot contain a NUL character.");
            }
        }
    }

    public static @NotNull String toJson(@NotNull List<EnvProfile> profiles) {
        JsonObject document = new JsonObject();
        JsonArray array = new JsonArray();
        for (EnvProfile profile : profiles) {
            JsonObject item = new JsonObject();
            item.addProperty("name", profile.getName());
            if (profile.getDescription() != null && !profile.getDescription().isEmpty()) {
                item.addProperty("description", profile.getDescription());
            }
            JsonObject env = new JsonObject();
            profile.getEnv().forEach(env::addProperty);
            item.add("env", env);
            array.add(item);
        }
        document.add("profiles", array);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document) + "\n";
    }
}
