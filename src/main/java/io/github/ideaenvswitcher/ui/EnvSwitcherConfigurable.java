package io.github.ideaenvswitcher.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import io.github.ideaenvswitcher.EnvSwitcherBundle;
import io.github.ideaenvswitcher.model.DotEnvParser;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Project settings: edits are staged until Apply; Reset reloads the source file. */
public final class EnvSwitcherConfigurable implements SearchableConfigurable {
    private final Project project;
    private JPanel panel;
    private DefaultListModel<Draft> profiles;
    private JBList<Draft> list;
    private JBTextField name;
    private JBTextField description;
    private DefaultTableModel variables;
    private JBTable table;
    private JButton addProfile;
    private JButton removeProfile;
    private JButton duplicateProfile;
    private JButton importFile;
    private JButton addVariable;
    private JButton removeVariable;
    private JLabel error;
    private @Nullable EnvProfileStore.Snapshot snapshot;
    private @Nullable String loadError;
    private @Nullable Draft editing;
    private boolean loading;

    public EnvSwitcherConfigurable(@NotNull Project project) { this.project = project; }
    @Override public @NotNull String getId() { return "io.github.ideaenvswitcher.settings"; }
    @Override public @NotNull String getDisplayName() { return "Env Switcher"; }

    @Override public JComponent createComponent() {
        if (panel != null) return panel;
        profiles = new DefaultListModel<>();
        list = new JBList<>(profiles);
        list.setName("profile.list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(event -> {
            if (loading || event.getValueIsAdjusting()) return;
            flush();
            display(list.getSelectedValue());
        });
        name = new JBTextField(); name.setName("profile.name");
        description = new JBTextField(); description.setName("profile.description");
        variables = new DefaultTableModel(new Object[]{message("settings.variable.name"), message("settings.variable.value")}, 0);
        table = new JBTable(variables); table.setName("profile.variables");
        table.putClientProperty("terminateEditOnFocusLost", true);
        addProfile = button("settings.profile.add", "profile.add", () -> {
            flush();
            int number = 1;
            String candidate;
            do { candidate = "profile" + number++; } while (hasName(candidate));
            profiles.addElement(new Draft(new EnvProfile(candidate, null, java.util.Map.of()), null));
            list.setSelectedIndex(profiles.size() - 1);
        });
        removeProfile = button("settings.profile.remove", "profile.remove", () -> {
            int index = list.getSelectedIndex();
            if (index >= 0) {
                profiles.remove(index);
                list.setSelectedIndex(Math.min(index, profiles.size() - 1));
                display(list.getSelectedValue());
            }
        });
        duplicateProfile = button("settings.profile.duplicate", "profile.duplicate", () -> {
            flush();
            if (editing == null) return;
            String base = (editing.name.isBlank() ? "profile" : editing.name.strip()) + " copy";
            addDraft(new Draft(editing, uniqueName(base)));
        });
        importFile = button("settings.profile.import", "profile.import", () -> {
            var descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle(message("settings.import.title"))
                    .withDescription(message("settings.import.description"))
                    .withShowHiddenFiles(true)
                    .withHideIgnored(false);
            var chosen = FileChooser.chooseFile(descriptor, panel, project, null);
            if (chosen != null) importProfile(chosen.toNioPath());
        });
        addVariable = button("settings.variable.add", "variable.add", () -> {
            stopEditing();
            variables.addRow(new Object[]{"", ""});
            table.setRowSelectionInterval(variables.getRowCount() - 1, variables.getRowCount() - 1);
        });
        removeVariable = button("settings.variable.remove", "variable.remove", () -> {
            stopEditing();
            int[] selected = table.getSelectedRows();
            for (int i = selected.length - 1; i >= 0; i--) variables.removeRow(table.convertRowIndexToModel(selected[i]));
        });
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.add(new JLabel(message("settings.profiles")), BorderLayout.NORTH);
        left.add(new JBScrollPane(list), BorderLayout.CENTER);
        JPanel profileButtons = new JPanel(new GridLayout(2, 2, 4, 4));
        for (JButton button : new JButton[]{addProfile, removeProfile, duplicateProfile, importFile}) profileButtons.add(button);
        left.add(profileButtons, BorderLayout.SOUTH);
        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 8));
        JLabel nameLabel = new JLabel(message("settings.profile.name")); nameLabel.setLabelFor(name);
        JLabel descriptionLabel = new JLabel(message("settings.profile.description")); descriptionLabel.setLabelFor(description);
        fields.add(nameLabel); fields.add(name); fields.add(descriptionLabel); fields.add(description);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.add(fields, BorderLayout.NORTH);
        right.add(new JBScrollPane(table), BorderLayout.CENTER);
        right.add(buttons(addVariable, removeVariable), BorderLayout.SOUTH);
        JBSplitter splitter = new JBSplitter(false, 0.36f);
        splitter.setFirstComponent(left); splitter.setSecondComponent(right);
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(720, 420));
        panel.add(new JLabel(message("settings.help")), BorderLayout.NORTH);
        panel.add(splitter, BorderLayout.CENTER);
        error = new JLabel(); error.setName("settings.error"); panel.add(error, BorderLayout.SOUTH);
        reset();
        return panel;
    }

    @Override public boolean isModified() {
        if (panel == null || snapshot == null) return false;
        try {
            return !EnvProfileStore.toJson(collect()).equals(EnvProfileStore.toJson(snapshot.profiles()));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Override public void apply() throws ConfigurationException {
        if (panel == null) return;
        if (loadError != null || snapshot == null) throw new ConfigurationException(loadError == null ? message("settings.load.error") : loadError);
        try {
            List<EnvProfile> edited = collect();
            EnvProfileStore.validate(edited);
            EnvSwitcherService service = EnvSwitcherService.getInstance(project);
            String current = service.getCurrentProfileName();
            String selected = null;
            for (int i = 0; i < profiles.size(); i++) {
                Draft draft = profiles.get(i);
                if (current != null && current.equals(draft.originalName)) selected = draft.name;
            }
            snapshot = service.saveProfiles(snapshot, edited, selected);
            for (int i = 0; i < profiles.size(); i++) profiles.get(i).originalName = profiles.get(i).name;
            error.setText("");
        } catch (EnvSwitcherService.ProfileSaveException e) {
            snapshot = e.getSavedSnapshot();
            for (int i = 0; i < profiles.size(); i++) profiles.get(i).originalName = profiles.get(i).name;
            throw new ConfigurationException(e.getMessage());
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override public void reset() {
        if (panel == null) return;
        loading = true;
        editing = null;
        snapshot = null;
        profiles.clear();
        loadError = null;
        try {
            if (project.getBasePath() == null) throw new IOException(message("settings.project.error"));
            snapshot = EnvProfileStore.load(Path.of(project.getBasePath(), EnvSwitcherService.PROFILES_FILE));
            for (EnvProfile profile : snapshot.profiles()) profiles.addElement(new Draft(profile, profile.getName()));
        } catch (IOException e) {
            loadError = e.getMessage();
        } finally {
            loading = false;
        }
        addProfile.setEnabled(snapshot != null);
        importFile.setEnabled(snapshot != null);
        error.setText(loadError == null ? "" : loadError);
        if (!profiles.isEmpty()) list.setSelectedIndex(0);
        display(list.getSelectedValue());
    }

    private List<EnvProfile> collect() {
        flush();
        List<EnvProfile> result = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            Draft draft = profiles.get(i);
            var env = new LinkedHashMap<String, String>();
            for (String[] row : draft.rows) {
                if (env.putIfAbsent(row[0], row[1]) != null) throw new IllegalArgumentException(message("settings.variable.duplicate"));
            }
            result.add(new EnvProfile(draft.name, draft.description, env));
        }
        return result;
    }

    /** Import into a new draft; only Apply persists it through the existing save path. */
    void importProfile(Path file) {
        if (panel == null || snapshot == null) return;
        try {
            var imported = DotEnvParser.parseFile(file);
            if (imported.isEmpty()) throw new IllegalArgumentException(message("settings.import.empty"));
            flush();
            String filename = file.getFileName().toString();
            String base = filename.equals(".env") ? "imported"
                    : filename.startsWith(".env.") ? filename.substring(5)
                    : filename.endsWith(".env") ? filename.substring(0, filename.length() - 4) : filename;
            base = base.replace('\r', ' ').replace('\n', ' ').strip();
            addDraft(new Draft(new EnvProfile(uniqueName(base.isEmpty() ? "imported" : base), null, imported), null));
            error.setText("");
        } catch (IOException e) {
            error.setText(message("settings.import.read.error"));
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
        }
    }

    private void addDraft(Draft draft) {
        profiles.addElement(draft);
        list.setSelectedIndex(profiles.size() - 1);
    }

    private String uniqueName(String base) {
        String candidate = base;
        for (int number = 2; hasName(candidate); number++) candidate = base + " (" + number + ")";
        return candidate;
    }

    private void flush() {
        if (editing == null || loading) return;
        stopEditing();
        editing.name = name.getText();
        editing.description = description.getText();
        editing.rows.clear();
        for (int i = 0; i < variables.getRowCount(); i++) {
            editing.rows.add(new String[]{String.valueOf(variables.getValueAt(i, 0)), String.valueOf(variables.getValueAt(i, 1))});
        }
        list.repaint();
    }

    private void display(@Nullable Draft draft) {
        editing = draft;
        name.setText(draft == null ? "" : draft.name);
        description.setText(draft == null ? "" : draft.description);
        variables.setRowCount(0);
        if (draft != null) for (String[] row : draft.rows) variables.addRow(row);
        boolean enabled = draft != null;
        name.setEnabled(enabled); description.setEnabled(enabled); table.setEnabled(enabled);
        removeProfile.setEnabled(enabled); addVariable.setEnabled(enabled); removeVariable.setEnabled(enabled);
        duplicateProfile.setEnabled(enabled);
    }

    private boolean hasName(String value) {
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).name.equals(value)) return true;
        return false;
    }

    private void stopEditing() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    private static JButton button(String key, String name, Runnable action) {
        JButton button = new JButton(message(key));
        button.setName(name); button.addActionListener(event -> action.run());
        return button;
    }

    private static JPanel buttons(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        for (JButton button : buttons) panel.add(button);
        return panel;
    }

    private static String message(String key) { return EnvSwitcherBundle.message(key); }

    @Override public void disposeUIResources() {
        panel = null; profiles = null; list = null; editing = null; snapshot = null;
        name = null; description = null; variables = null; table = null;
        addProfile = null; removeProfile = null; addVariable = null; removeVariable = null; error = null;
        duplicateProfile = null; importFile = null;
    }

    private static final class Draft {
        private String originalName;
        private String name;
        private String description;
        private final List<String[]> rows = new ArrayList<>();
        private Draft(EnvProfile profile, @Nullable String originalName) {
            this.originalName = originalName;
            name = profile.getName();
            description = profile.getDescription() == null ? "" : profile.getDescription();
            profile.getEnv().forEach((key, value) -> rows.add(new String[]{key, value}));
        }
        private Draft(Draft source, String name) {
            this.name = name;
            description = source.description;
            for (String[] row : source.rows) rows.add(row.clone());
        }
        @Override public String toString() { return name.isEmpty() ? message("settings.profile.unnamed") : name; }
    }
}
