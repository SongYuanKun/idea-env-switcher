package io.github.ideaenvswitcher.service;

import io.github.ideaenvswitcher.model.EnvProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunConfigurationEnvInjectorTest {

    @Test
    void mergeOverlaysProfileKeysAndKeepsOthers() {
        Map<String, String> existing = new LinkedHashMap<>();
        existing.put("PATH", "/usr/bin");
        existing.put("APP_ENV", "local");

        Map<String, String> profileEnv = new LinkedHashMap<>();
        profileEnv.put("APP_ENV", "dev");
        profileEnv.put("API_BASE", "http://localhost:8080");
        EnvProfile profile = new EnvProfile("dev", null, profileEnv);

        Map<String, String> merged = RunConfigurationEnvInjector.merge(existing, profile);

        assertEquals("/usr/bin", merged.get("PATH"));
        assertEquals("dev", merged.get("APP_ENV"));
        assertEquals("http://localhost:8080", merged.get("API_BASE"));
    }

    @Test
    void mergeWithNullProfileReturnsCopy() {
        Map<String, String> existing = Map.of("A", "1");
        Map<String, String> merged = RunConfigurationEnvInjector.merge(existing, null);
        assertEquals(Map.of("A", "1"), merged);
        assertTrue(merged != existing);
    }
}
