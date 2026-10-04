package io.github.ideaenvswitcher.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EnvSwitcherConfigurableTest {
    @Rule public final EnvProjectRule projectRule = new EnvProjectRule();
    @Rule public final EdtRule edtRule = new EdtRule();

    @Test
    @RunsInEdt
    public void stagesChangesUntilApplyAndResetDiscardsEdits() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(new EnvProfile("dev", null, Map.of("KEY", "old"))));
        var configurable = new EnvSwitcherConfigurable(projectRule.getProject());
        JComponent panel = configurable.createComponent();
        try {
            assertFalse(configurable.isModified());
            JTextField name = (JTextField) find(panel, "profile.name");
            name.setText("staged");
            assertTrue(configurable.isModified());
            assertEquals("dev", EnvProfileStore.load(file).profiles().getFirst().getName());
            configurable.reset();
            assertEquals("dev", name.getText());
            assertFalse(configurable.isModified());
            name.setText("saved");
            configurable.apply();
            assertEquals("saved", EnvProfileStore.load(file).profiles().getFirst().getName());
            assertFalse(configurable.isModified());
            JButton addVariable = (JButton) find(panel, "variable.add");
            addVariable.doClick();
            JTable table = (JTable) find(panel, "profile.variables");
            table.setValueAt("KEY", 1, 0);
            assertThrows(ConfigurationException.class, configurable::apply);
            assertEquals(1, EnvProfileStore.load(file).profiles().getFirst().getEnv().size());
        } finally {
            configurable.disposeUIResources();
        }
    }

    @Test
    @RunsInEdt
    public void malformedFileIsShownAndCannotBeOverwritten() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.writeString(file, "invalid json");
        var configurable = new EnvSwitcherConfigurable(projectRule.getProject());
        JComponent panel = configurable.createComponent();
        try {
            assertFalse(((JButton) find(panel, "profile.add")).isEnabled());
            assertThrows(ConfigurationException.class, configurable::apply);
            assertEquals("invalid json", Files.readString(file));
        } finally {
            configurable.disposeUIResources();
        }
    }

    @Test
    @RunsInEdt
    public void duplicatesStagedValuesIndependentlyWithUniqueNamesAndPreservesActiveSelection() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(new EnvProfile("dev", "Local", Map.of("KEY", "old"))));
        var service = EnvSwitcherService.getInstance(projectRule.getProject());
        service.switchTo(service.findByName("dev"));
        String originalEnv = Files.readString(base.resolve(".env"));
        var configurable = new EnvSwitcherConfigurable(projectRule.getProject());
        JComponent panel = configurable.createComponent();
        try {
            JButton duplicate = (JButton) find(panel, "profile.duplicate");
            assertNotNull("Duplicate profile action is available", duplicate);
            JTable table = (JTable) find(panel, "profile.variables");
            JTextField name = (JTextField) find(panel, "profile.name");
            JTextField description = (JTextField) find(panel, "profile.description");
            table.setValueAt("staged", 0, 1);
            duplicate.doClick();
            assertEquals("dev copy", name.getText());
            assertEquals("Local", description.getText());
            assertEquals("staged", table.getValueAt(0, 1));
            table.setValueAt("copy only", 0, 1);
            JList<?> list = (JList<?>) find(panel, "profile.list");
            list.setSelectedIndex(0);
            assertEquals("staged", table.getValueAt(0, 1));
            duplicate.doClick();
            assertEquals("dev copy (2)", name.getText());
            assertEquals(1, EnvProfileStore.load(file).profiles().size());
            assertEquals(originalEnv, Files.readString(base.resolve(".env")));
            configurable.apply();
            var saved = EnvProfileStore.load(file).profiles();
            assertEquals(List.of("dev", "dev copy", "dev copy (2)"), saved.stream().map(EnvProfile::getName).toList());
            assertEquals("copy only", saved.get(1).getEnv().get("KEY"));
            assertEquals("dev", service.getCurrentProfileName());
            assertEquals("staged", service.getCurrentProfile().getEnv().get("KEY"));
            assertFalse(configurable.isModified());
        } finally {
            configurable.disposeUIResources();
        }
    }

    @Test
    @RunsInEdt
    public void importsAsNewStagedProfilesAndResetDiscardsThemWithoutChangingSource() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        Path source = base.resolve(".env.staging");
        String content = "APP_MODE=staging\nMESSAGE=\"hello # world\"\n";
        Files.writeString(source, content);
        var configurable = new EnvSwitcherConfigurable(projectRule.getProject());
        JComponent panel = configurable.createComponent();
        try {
            configurable.importProfile(source);
            assertTrue(configurable.isModified());
            assertFalse(Files.exists(file));
            assertEquals("staging", ((JTextField) find(panel, "profile.name")).getText());
            assertEquals("hello # world", ((JTable) find(panel, "profile.variables")).getValueAt(1, 1));
            configurable.reset();
            assertFalse(configurable.isModified());
            assertEquals(0, ((JTable) find(panel, "profile.variables")).getRowCount());
            configurable.importProfile(source);
            configurable.importProfile(source);
            assertEquals("staging (2)", ((JTextField) find(panel, "profile.name")).getText());
            configurable.apply();
            var saved = EnvProfileStore.load(file).profiles();
            assertEquals(List.of("staging", "staging (2)"), saved.stream().map(EnvProfile::getName).toList());
            assertEquals(Map.of("APP_MODE", "staging", "MESSAGE", "hello # world"), saved.getFirst().getEnv());
            assertEquals(content, Files.readString(source));
            assertNull(EnvSwitcherService.getInstance(projectRule.getProject()).getCurrentProfileName());
        } finally {
            configurable.disposeUIResources();
        }
    }

    @Test
    @RunsInEdt
    public void failedImportsPreserveAllDraftsAndShowValueFreeErrors() throws Exception {
        Path base = Path.of(projectRule.getProject().getBasePath());
        Files.createDirectories(base);
        Path file = base.resolve("env-profiles.json");
        Files.deleteIfExists(file);
        EnvProfileStore.save(file, EnvProfileStore.load(file), List.of(new EnvProfile("dev", null, Map.of("KEY", "old"))));
        var configurable = new EnvSwitcherConfigurable(projectRule.getProject());
        JComponent panel = configurable.createComponent();
        Path source = base.resolve("bad.env");
        try {
            JTextField name = (JTextField) find(panel, "profile.name");
            name.setText("unsaved");
            Files.writeString(source, "GOOD=value\nBAD-KEY=private-fixture\n");
            configurable.importProfile(source);
            JLabel error = (JLabel) find(panel, "settings.error");
            assertNotNull(error);
            assertTrue(error.getText().contains("line 2"));
            assertFalse(error.getText().contains("private-fixture"));
            assertEquals("unsaved", name.getText());
            assertEquals(1, ((JList<?>) find(panel, "profile.list")).getModel().getSize());
            Files.writeString(source, "# comments only\n");
            configurable.importProfile(source);
            assertFalse(error.getText().isEmpty());
            configurable.importProfile(base.resolve("does-not-exist.env"));
            assertFalse(error.getText().isEmpty());
            assertEquals("unsaved", name.getText());
            configurable.apply();
            assertEquals(1, EnvProfileStore.load(file).profiles().size());
            assertEquals("unsaved", EnvProfileStore.load(file).profiles().getFirst().getName());
        } finally {
            configurable.disposeUIResources();
        }
    }

    private static Component find(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
