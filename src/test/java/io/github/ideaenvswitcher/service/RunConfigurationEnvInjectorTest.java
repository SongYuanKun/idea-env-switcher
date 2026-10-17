package io.github.ideaenvswitcher.service;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleProgramParameters;
import io.github.ideaenvswitcher.model.EnvProfile;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RunConfigurationEnvInjectorTest {
    @Test
    void commandLineOverlaysKeysAndKeepsOthers() {
        var commandLine = new GeneralCommandLine().withEnvironment(Map.of("KEEP", "original", "KEY", "old"));
        RunConfigurationEnvInjector.applyProfile(commandLine, new EnvProfile("dev", null, Map.of("KEY", "new")));
        assertEquals(Map.of("KEEP", "original", "KEY", "new"), commandLine.getEnvironment());
    }

    @Test
    void parametersOverlayKeysAndKeepOthers() {
        var parameters = new SimpleProgramParameters();
        parameters.getEnv().putAll(Map.of("KEEP", "original", "KEY", "old"));
        RunConfigurationEnvInjector.applyProfile(parameters, new EnvProfile("dev", null, Map.of("KEY", "new")));
        assertEquals(Map.of("KEEP", "original", "KEY", "new"), parameters.getEnv());
    }

    @Test
    void nullAndEmptyProfilesLeaveBothTargetsUnchanged() {
        var commandLine = new GeneralCommandLine().withEnvironment("KEY", "old");
        var parameters = new SimpleProgramParameters();
        parameters.getEnv().put("KEY", "old");
        RunConfigurationEnvInjector.applyProfile(commandLine, null);
        RunConfigurationEnvInjector.applyProfile(parameters, null);
        assertEquals(Map.of("KEY", "old"), commandLine.getEnvironment());
        assertEquals(Map.of("KEY", "old"), parameters.getEnv());
        var empty = new EnvProfile("empty", null, Map.of());
        RunConfigurationEnvInjector.applyProfile(commandLine, empty);
        RunConfigurationEnvInjector.applyProfile(parameters, empty);
        assertEquals(Map.of("KEY", "old"), commandLine.getEnvironment());
        assertEquals(Map.of("KEY", "old"), parameters.getEnv());
    }
}
