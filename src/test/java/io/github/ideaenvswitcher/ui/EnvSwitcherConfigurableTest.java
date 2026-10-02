package io.github.ideaenvswitcher.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.EdtRule;
import io.github.ideaenvswitcher.test.EnvProjectRule;
import com.intellij.testFramework.RunsInEdt;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
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
