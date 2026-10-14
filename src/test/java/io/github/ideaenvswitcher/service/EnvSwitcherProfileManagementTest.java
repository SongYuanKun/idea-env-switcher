package io.github.ideaenvswitcher.service;

import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EnvSwitcherProfileManagementTest {
    @Rule public final EnvProjectRule projectRule = new EnvProjectRule();
    @Rule public final EdtRule edtRule = new EdtRule();

    @Before
    @After
    public void cleanEnvironmentFixtures() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        for (String filename : List.of(".env", ".env.backup", "target.env")) Files.deleteIfExists(base.resolve(filename));
    }

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

    @Test
    @RunsInEdt
    public void backsUpManualFileAndRefusesASecondBackupWithoutChangingSelection() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        Path env = base.resolve(".env");
        Path backup = base.resolve(".env.backup");
        byte[] original = {'C', '=', (byte) 0xff, '\r', '\n'};
        Files.write(env, original);
        var first = new EnvProfile("first", null, Map.of("KEY", "one"));
        service.switchTo(first);
        assertArrayEquals(original, Files.readAllBytes(backup));
        assertEquals(DotEnvWriter.toDotEnvContent(first), Files.readString(env));
        byte[] manual = "CUSTOM=second\n".getBytes(StandardCharsets.UTF_8);
        Files.write(env, manual);
        var next = new EnvProfile("next", null, Map.of("KEY", "two"));
        var failure = assertThrows(IOException.class, () -> service.switchTo(next));
        assertEquals("Existing .env was not generated by Env Switcher and .env.backup already exists. Move one of them and switch again.", failure.getMessage());
        assertArrayEquals(manual, Files.readAllBytes(env));
        assertArrayEquals(original, Files.readAllBytes(backup));
        assertEquals("first", service.getCurrentProfileName());
        assertEquals("first", EnvSwitcherWorkspaceState.getInstance(projectRule.getProject()).getLastProfileName());
    }

    @Test
    @RunsInEdt
    public void overwritesGeneratedFileWithoutBackup() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.switchTo(new EnvProfile("first", null, Map.of("KEY", "one")));
        var next = new EnvProfile("next", null, Map.of("KEY", "two"));
        service.switchTo(next);
        assertEquals(DotEnvWriter.toDotEnvContent(next), Files.readString(base.resolve(".env")));
        assertFalse(Files.exists(base.resolve(".env.backup")));
    }

    @Test
    @RunsInEdt
    public void refusesSymbolicEnvAndBackupLinks() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        Path target = base.resolve("target.env");
        Files.writeString(target, "CUSTOM=original\n");
        Path env = base.resolve(".env");
        try {
            Files.createSymbolicLink(env, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assume.assumeNoException(e);
        }
        var profile = new EnvProfile("dev", null, Map.of("KEY", "one"));
        assertEquals(".env must be a regular file, not a symbolic link.",
                assertThrows(IOException.class, () -> service.switchTo(profile)).getMessage());
        assertEquals("CUSTOM=original\n", Files.readString(target));
        assertTrue(Files.isSymbolicLink(env));
        Files.delete(env);
        Files.writeString(env, "CUSTOM=manual\n");
        Files.createSymbolicLink(base.resolve(".env.backup"), base.resolve("missing.env"));
        assertThrows(IOException.class, () -> service.switchTo(profile));
        assertEquals("CUSTOM=manual\n", Files.readString(env));
        assertNull(service.getCurrentProfileName());
    }

    @Test
    @RunsInEdt
    public void protectionConflictDuringSaveAndRestoreKeepsFilesAndReportsCause() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        var first = new EnvProfile("first", null, Map.of("KEY", "one"));
        var next = new EnvProfile("next", null, Map.of("KEY", "two"));
        var snapshot = EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(first, next));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        service.switchTo(first);
        Files.writeString(base.resolve(".env"), "CUSTOM=manual\n");
        Files.writeString(base.resolve(".env.backup"), "CUSTOM=backup\n");
        var failure = assertThrows(EnvSwitcherService.ProfileSaveException.class,
                () -> service.saveProfiles(snapshot, List.of(first, next), "next"));
        assertTrue(failure.getMessage().contains(".env.backup already exists"));
        // 配置已保存，选择反映保存结果；.env 与备份保持原样
        assertEquals("next", service.getCurrentProfileName());
        service.restorePersistedProfile();
        assertEquals("next", service.getCurrentProfileName());
        assertEquals("CUSTOM=manual\n", Files.readString(base.resolve(".env")));
        assertEquals("CUSTOM=backup\n", Files.readString(base.resolve(".env.backup")));
    }

    @Test
    @RunsInEdt
    public void reloadPublishesImmutableProfileSnapshots() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        var first = new EnvProfile("first", null, Map.of());
        var snapshot = EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(first));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.reloadProfiles();
        List<EnvProfile> previous = service.getProfiles();
        var next = new EnvProfile("next", null, Map.of());
        EnvProfileStore.save(file, snapshot, List.of(next));
        service.reloadProfiles();
        assertEquals("first", previous.getFirst().getName());
        assertEquals("next", service.getProfiles().getFirst().getName());
        assertThrows(UnsupportedOperationException.class, () -> previous.add(next));
    }
}
