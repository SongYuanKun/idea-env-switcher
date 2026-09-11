package io.github.ideaenvswitcher.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 管理当前项目的环境配置加载与切换。 */
@Service(Service.Level.PROJECT)
public final class EnvSwitcherService {

    public static final String PROFILES_FILE = "env-profiles.json";
    public static final String DOT_ENV_FILE = ".env";

    private static final Logger LOG = Logger.getInstance(EnvSwitcherService.class);

    private final Project project;
    private final List<EnvProfile> profiles = new CopyOnWriteArrayList<>();
    private volatile @Nullable String currentProfileName;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public EnvSwitcherService(@NotNull Project project) {
        this.project = project;
        reloadProfiles();
    }

    public static @NotNull EnvSwitcherService getInstance(@NotNull Project project) {
        return project.getService(EnvSwitcherService.class);
    }

    public @NotNull List<EnvProfile> getProfiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    public @Nullable String getCurrentProfileName() {
        return currentProfileName;
    }

    public @Nullable EnvProfile findByName(@NotNull String name) {
        for (EnvProfile profile : profiles) {
            if (profile.getName().equals(name)) {
                return profile;
            }
        }
        return null;
    }

    public void addChangeListener(@NotNull Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(@NotNull Runnable listener) {
        listeners.remove(listener);
    }

    /** 重新读取 env-profiles.json。 */
    public int reloadProfiles() {
        profiles.clear();
        Path path = profilesPath();
        if (path == null || !Files.isRegularFile(path)) {
            fireChanged();
            return 0;
        }
        try {
            profiles.addAll(EnvProfileParser.parseFile(path));
            if (currentProfileName != null && findByName(currentProfileName) == null) {
                currentProfileName = null;
                EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(null);
            }
            fireChanged();
            return profiles.size();
        } catch (Exception e) {
            LOG.warn("Failed to load " + PROFILES_FILE, e);
            fireChanged();
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** 切换到指定配置，写入 .env，并持久化到 workspace。 */
    public void switchTo(@NotNull EnvProfile profile) throws IOException {
        applyProfile(profile, true);
    }

    /**
     * 根据 workspace 中记录的上次选择恢复环境。
     * 若 profile 仍存在，则同步写入 .env。
     */
    public void restorePersistedProfile() {
        String persisted = EnvSwitcherWorkspaceState.getInstance(project).getLastProfileName();
        if (persisted == null || persisted.isBlank()) {
            return;
        }
        EnvProfile profile = findByName(persisted);
        if (profile == null) {
            currentProfileName = null;
            EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(null);
            fireChanged();
            return;
        }
        try {
            applyProfile(profile, false);
        } catch (IOException e) {
            LOG.warn("Failed to restore profile '" + persisted + "' into .env", e);
            currentProfileName = profile.getName();
            fireChanged();
        }
    }

    private void applyProfile(@NotNull EnvProfile profile, boolean persist) throws IOException {
        Path baseDir = projectBasePath();
        if (baseDir == null) {
            throw new IOException("Project base path is unavailable");
        }
        Path envFile = baseDir.resolve(DOT_ENV_FILE);
        Files.writeString(envFile, DotEnvWriter.toDotEnvContent(profile), StandardCharsets.UTF_8);
        refreshVirtualFile(envFile);
        currentProfileName = profile.getName();
        if (persist) {
            EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(profile.getName());
        }
        fireChanged();
    }

    private void refreshVirtualFile(@NotNull Path path) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (file != null) {
            file.refresh(false, false);
        }
    }

    private @Nullable Path profilesPath() {
        Path base = projectBasePath();
        return base == null ? null : base.resolve(PROFILES_FILE);
    }

    private @Nullable Path projectBasePath() {
        String basePath = project.getBasePath();
        return basePath == null ? null : Path.of(basePath);
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOG.warn("Env switcher listener failed", e);
            }
        }
    }
}
