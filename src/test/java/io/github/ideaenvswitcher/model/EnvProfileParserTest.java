package io.github.ideaenvswitcher.model;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvProfileParserTest {

    @Test
    void parsesProfiles() {
        String json = """
                {
                  "profiles": [
                    {
                      "name": "dev",
                      "description": "Local",
                      "env": { "APP_ENV": "dev", "PORT": "8080" }
                    },
                    {
                      "name": "prod",
                      "env": { "APP_ENV": "prod" }
                    }
                  ]
                }
                """;
        List<EnvProfile> profiles = EnvProfileParser.parse(new StringReader(json));
        assertEquals(2, profiles.size());
        assertEquals("dev", profiles.get(0).getName());
        assertEquals("Local", profiles.get(0).getDescription());
        assertEquals("8080", profiles.get(0).getEnv().get("PORT"));
        assertEquals("prod", profiles.get(1).getName());
    }

    @Test
    void rejectsMissingProfilesField() {
        assertThrows(IllegalArgumentException.class,
                () -> EnvProfileParser.parse(new StringReader("{}")));
    }
}
