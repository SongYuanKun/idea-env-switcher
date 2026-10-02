package io.github.ideaenvswitcher.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import io.github.ideaenvswitcher.model.EnvProfile;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvSwitcherEdtThreadingTest {

    @Rule
    public final EnvProjectRule projectRule = new EnvProjectRule();

    @Rule
    public final EdtRule edtRule = new EdtRule();

    @Test
    @RunsInEdt
    public void switchesProfileOnEdtWithoutWriteAccess() throws Exception {
        assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());
        Path basePath = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(basePath);
        EnvProfile profile = new EnvProfile("dev", null, Map.of("APP_ENV", "dev"));

        EnvSwitcherService service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.switchTo(profile);

        assertEquals("dev", service.getCurrentProfileName());
        assertTrue(Files.readString(basePath.resolve(".env")).contains("APP_ENV=dev"));
    }
}
