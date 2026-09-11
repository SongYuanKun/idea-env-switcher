package io.github.ideaenvswitcher.service;

import io.github.ideaenvswitcher.model.EnvProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotEnvWriterTest {

    @Test
    void writesProfileHeaderAndEntries() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("APP_ENV", "dev");
        env.put("API_BASE", "http://localhost:8080");
        EnvProfile profile = new EnvProfile("dev", "Local", env);

        String content = DotEnvWriter.toDotEnvContent(profile);
        assertTrue(content.contains("# profile=dev"));
        assertTrue(content.contains("APP_ENV=dev"));
        assertTrue(content.contains("API_BASE=http://localhost:8080"));
    }

    @Test
    void quotesValuesWithSpaces() {
        assertEquals("\"hello world\"", DotEnvWriter.escapeValue("hello world"));
        assertEquals("plain", DotEnvWriter.escapeValue("plain"));
        assertEquals("\"\"", DotEnvWriter.escapeValue(""));
    }
}
