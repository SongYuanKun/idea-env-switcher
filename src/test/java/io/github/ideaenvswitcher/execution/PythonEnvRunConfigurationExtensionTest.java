package io.github.ideaenvswitcher.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.run.PythonRunConfigurationExtension;
import com.jetbrains.python.run.PythonRunConfigurationExtensionsManager;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.jdom.Element;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PythonEnvRunConfigurationExtensionTest {
    @Rule public final EnvProjectRule projectRule = new EnvProjectRule();
    @Rule public final EdtRule edtRule = new EdtRule();

    @Test
    @RunsInEdt
    public void registeredExtensionInjectsIntoPythonProcessWithoutChangingSavedConfiguration() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve(EnvSwitcherService.PROFILES_FILE);
        Files.deleteIfExists(file);
        var profile = new EnvProfile("dev", null, Map.of("APP_ENV", "profile"));
        EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(profile));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        service.switchTo(profile);
        PythonRunConfiguration configuration = (PythonRunConfiguration) PythonConfigurationType.getInstance()
                .getFactory().createTemplateConfiguration(projectRule.getProject());
        configuration.setEnvs(Map.of("APP_ENV", "configuration", "KEEP", "retained"));
        String before = xml(configuration);
        assertTrue(ExtensionPointName.<PythonRunConfigurationExtension>create("Pythonid.runConfigurationExtension").getExtensionList().stream()
                .anyMatch(PythonEnvRunConfigurationExtension.class::isInstance));
        GeneralCommandLine command = new GeneralCommandLine("python3", "-c",
                "import os; print(os.environ['APP_ENV']); print(os.environ['KEEP'])");
        command.withEnvironment(configuration.getEnvs());

        PythonRunConfigurationExtensionsManager.getInstance().patchCommandLine(configuration, null, command, "PythonRunner");
        var output = new CapturingProcessHandler(command).runProcess(10000);

        assertEquals(output.getStderr(), 0, output.getExitCode());
        assertFalse(output.isTimeout());
        assertEquals("profile\nretained\n", output.getStdout().replace("\r\n", "\n"));
        assertEquals("configuration", configuration.getEnvs().get("APP_ENV"));
        assertEquals(before, xml(configuration));
        service.saveProfiles(EnvProfileStore.load(file), List.of(), null);
        GeneralCommandLine withoutProfile = new GeneralCommandLine().withEnvironment("KEEP", "original");
        PythonRunConfigurationExtensionsManager.getInstance().patchCommandLine(configuration, null, withoutProfile, "PythonRunner");
        assertEquals(Map.of("KEEP", "original"), withoutProfile.getEnvironment());
    }

    private static String xml(PythonRunConfiguration configuration) throws Exception {
        Element element = new Element("configuration");
        configuration.writeExternal(element);
        return JDOMUtil.writeElement(element);
    }
}
