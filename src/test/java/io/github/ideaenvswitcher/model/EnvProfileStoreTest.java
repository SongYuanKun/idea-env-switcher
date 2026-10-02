package io.github.ideaenvswitcher.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvProfileStoreTest {
    @TempDir Path directory;

    @Test
    void createsAndRoundTripsProfilesWithoutLosingSpecialCharacters() throws Exception {
        Path file = directory.resolve("env-profiles.json");
        var initial = EnvProfileStore.load(file);
        assertTrue(initial.profiles().isEmpty());
        var profile = new EnvProfile("dev", "开发环境", Map.of("MESSAGE", "a\"b\nc\\d", "EMPTY", ""));
        var saved = EnvProfileStore.save(file, initial, List.of(profile));
        assertEquals(profile.getDescription(), saved.profiles().getFirst().getDescription());
        assertEquals(profile.getEnv(), saved.profiles().getFirst().getEnv());
        assertEquals(profile.getEnv(), EnvProfileParser.parseFile(file).getFirst().getEnv());
    }

    @Test
    void refusesToOverwriteAnExternalEdit() throws Exception {
        Path file = directory.resolve("env-profiles.json");
        var initial = EnvProfileStore.load(file);
        String external = "{\"profiles\": [{\"name\":\"external\"}]}";
        Files.writeString(file, external);
        assertThrows(IOException.class, () -> EnvProfileStore.save(file, initial, List.of()));
        assertEquals(external, Files.readString(file));
    }

    @Test
    void rejectsMalformedDocumentsAndInvalidProfiles() throws Exception {
        Path file = directory.resolve("env-profiles.json");
        Files.writeString(file, "{\"profiles\": [false]}");
        assertThrows(IOException.class, () -> EnvProfileStore.load(file));
        var dev = new EnvProfile("dev", null, Map.of("APP_ENV", "dev"));
        assertThrows(IllegalArgumentException.class, () -> EnvProfileStore.validate(List.of(dev, dev)));
        assertThrows(IllegalArgumentException.class, () -> EnvProfileStore.validate(List.of(
                new EnvProfile("bad\nname", null, Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> EnvProfileStore.validate(List.of(
                new EnvProfile("dev", null, Map.of("INVALID=KEY", "value")))));
        assertThrows(IllegalArgumentException.class, () -> EnvProfileStore.validate(List.of(
                new EnvProfile("dev", null, Map.of("KEY", "value\0")))));
    }

    @Test
    void canSaveAnEmptyListAndDetectDeletionOfAnExistingFile() throws Exception {
        Path file = directory.resolve("env-profiles.json");
        var saved = EnvProfileStore.save(file, EnvProfileStore.load(file), List.of());
        assertTrue(EnvProfileStore.load(file).profiles().isEmpty());
        Files.delete(file);
        assertThrows(IOException.class, () -> EnvProfileStore.save(file, saved, List.of()));
    }
}
