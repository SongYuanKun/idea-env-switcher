package io.github.ideaenvswitcher.service;

import com.intellij.testFramework.ProjectRule;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class EnvSwitcherStartupActivityTest {

    @Rule
    public final ProjectRule projectRule = new ProjectRule();

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
}
