package io.github.ideaenvswitcher.service;

import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EnvSwitcherProfileManagementTest {
    @Rule public final EnvProjectRule projectRule = new EnvProjectRule();
    @Rule public final EdtRule edtRule = new EdtRule();

    @Test
    @RunsInEdt
    public void savingActiveEditsAndRenameUpdatesDotEnvAndWorkspace() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve(EnvSwitcherService.PROFILES_FILE);
        Files.deleteIfExists(file);
        var original = new EnvProfile("dev", null, Map.of("APP_ENV", "dev"));
        var snapshot = EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(original));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        service.switchTo(original);
        var renamed = new EnvProfile("local", null, Map.of("APP_ENV", "local"));

        service.saveProfiles(snapshot, List.of(renamed), "local");

        assertEquals("local", service.getCurrentProfileName());
        assertEquals("local", EnvSwitcherWorkspaceState.getInstance(projectRule.getProject()).getLastProfileName());
        assertEquals("local", service.getProfiles().getFirst().getName());
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_ENV=local"));
        service.saveProfiles(EnvProfileStore.load(file), List.of(), null);
        assertNull(service.getCurrentProfileName());
        assertNull(EnvSwitcherWorkspaceState.getInstance(projectRule.getProject()).getLastProfileName());
        assertFalse(Files.readString(base.resolve(".env")).contains("APP_ENV="));
    }

    @Test
    @RunsInEdt
    public void failedReloadRetainsWorkingProfilesAndMissingFileClearsSelection() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve(EnvSwitcherService.PROFILES_FILE);
        Files.deleteIfExists(file);
        var profile = new EnvProfile("dev", null, Map.of("APP_ENV", "dev"));
        EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(profile));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        service.switchTo(profile);
        Files.writeString(file, "invalid json");
        assertThrows(IllegalStateException.class, service::reloadProfiles);
        assertEquals("dev", service.getProfiles().getFirst().getName());
        assertEquals("dev", service.getCurrentProfileName());
        Files.writeString(file, "{\"profiles\": [{\"name\":\"dev\", \"env\":{\"APP_ENV\":\"edited\"}}]}");
        service.reloadProfiles();
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_ENV=edited"));
        Files.delete(file);
        service.reloadProfiles();
        assertTrue(service.getProfiles().isEmpty());
        assertNull(service.getCurrentProfileName());
        assertNull(EnvSwitcherWorkspaceState.getInstance(projectRule.getProject()).getLastProfileName());
        assertFalse(Files.readString(base.resolve(".env")).contains("APP_ENV="));
    }

    @Test
    @RunsInEdt
    public void reportsPartialSaveAndAllowsRetryWithoutOverwritingManualDotEnvChanges() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve(EnvSwitcherService.PROFILES_FILE);
        Files.deleteIfExists(file);
        var original = new EnvProfile("dev", null, Map.of("APP_ENV", "dev"));
        var snapshot = EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(original));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        service.switchTo(original);
        Path envFile = base.resolve(".env");
        Files.delete(envFile);
        Files.createDirectory(envFile);
        var edited = new EnvProfile("local", null, Map.of("APP_ENV", "local"));
        EnvSwitcherService.ProfileSaveException failure;
        try {
            failure = assertThrows(EnvSwitcherService.ProfileSaveException.class,
                    () -> service.saveProfiles(snapshot, List.of(edited), "local"));
            assertEquals("local", EnvProfileStore.load(file).profiles().getFirst().getName());
            assertEquals("local", service.getCurrentProfileName());
        } finally {
            Files.delete(envFile);
        }
        service.saveProfiles(failure.getSavedSnapshot(), List.of(edited), "local");
        assertTrue(Files.readString(envFile).contains("APP_ENV=local"));
        String manual = "# custom configuration\nMANUAL=yes\n";
        Files.writeString(envFile, manual);
        service.saveProfiles(EnvProfileStore.load(file), List.of(), null);
        assertEquals(manual, Files.readString(envFile));
        assertNull(service.getCurrentProfileName());
    }
}
