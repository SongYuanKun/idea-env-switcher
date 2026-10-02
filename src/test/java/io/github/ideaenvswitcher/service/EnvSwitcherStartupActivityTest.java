package io.github.ideaenvswitcher.service;

import io.github.ideaenvswitcher.test.EnvProjectRule;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EnvSwitcherStartupActivityTest {

    @Rule
    public final EnvProjectRule projectRule = new EnvProjectRule();

    @Test
    public void reloadsProfilesWhenProjectStartupCompletes() throws Exception {
        Path basePath = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(basePath);
        Files.deleteIfExists(basePath.resolve("env-profiles.json"));
        EnvSwitcherService service = EnvSwitcherService.getInstance(projectRule.getProject());
        assertEquals(0, service.getProfiles().size());

        Files.writeString(basePath.resolve("env-profiles.json"), """
                {"profiles":[{"name":"dev","env":{"APP_ENV":"dev"}}]}
                """);

        new EnvSwitcherStartupActivity().runActivity(projectRule.getProject());

        assertEquals(1, service.getProfiles().size());
    }

    @Test
    public void invalidProfilesDoNotAbortProjectStartup() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Files.writeString(base.resolve("env-profiles.json"), "invalid json");
        new EnvSwitcherStartupActivity().runActivity(projectRule.getProject());
        assertNull(EnvSwitcherService.getInstance(projectRule.getProject()).getCurrentProfileName());
    }
}
