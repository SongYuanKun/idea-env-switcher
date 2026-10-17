package io.github.ideaenvswitcher.service;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.ideaenvswitcher.EnvNotifications;
import io.github.ideaenvswitcher.EnvSwitcherBundle;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.model.EnvProfileStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 管理当前项目的环境配置加载与切换。 */
@Service(Service.Level.PROJECT)
public final class EnvSwitcherService {

    public static final String PROFILES_FILE = "env-profiles.json";
    public static final String DOT_ENV_FILE = ".env";

    private static final Logger LOG = Logger.getInstance(EnvSwitcherService.class);

    private final Project project;
    private volatile List<EnvProfile> profiles = List.of();
    private volatile @Nullable String currentProfileName;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public EnvSwitcherService(@NotNull Project project) {
        this.project = project;
        try {
            reloadProfiles();
        } catch (IllegalStateException e) {
            LOG.warn("Invalid profiles file; correct it before reloading.");
        }
    }

    public static @NotNull EnvSwitcherService getInstance(@NotNull Project project) {
        return project.getService(EnvSwitcherService.class);
    }

    public @NotNull List<EnvProfile> getProfiles() {
        return profiles;
    }

    public @Nullable String getCurrentProfileName() {
        return currentProfileName;
    }

    public synchronized @Nullable EnvProfile getCurrentProfile() {
        return currentProfileName == null ? null : findByName(currentProfileName);
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
    public synchronized int reloadProfiles() {
        Path path = profilesPath();
        EnvProfile previous = getCurrentProfile();
        if (path == null || !Files.isRegularFile(path)) {
            profiles = List.of();
            clearSelection();
            try {
                clearGeneratedEnvironment(previous);
            } catch (IOException e) {
                throw new IllegalStateException("Profiles were reloaded, but .env could not be updated. Check project write permissions.");
            } finally {
                fireChanged();
            }
            return 0;
        }
        try {
            List<EnvProfile> loaded = EnvProfileStore.load(path).profiles();
            profiles = List.copyOf(loaded);
            if (currentProfileName != null && findByName(currentProfileName) == null) {
                clearSelection();
            }
            EnvProfile selected = getCurrentProfile();
            try {
                if (selected == null) clearGeneratedEnvironment(previous);
                else applyProfile(selected, true);
            } catch (IOException e) {
                throw new IllegalStateException("Profiles were reloaded, but .env could not be updated. Check project write permissions.");
            } finally {
                fireChanged();
            }
            return profiles.size();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** Save staged settings and refresh the selected environment, including renames. */
    public synchronized @NotNull EnvProfileStore.Snapshot saveProfiles(
            @NotNull EnvProfileStore.Snapshot expected, @NotNull List<EnvProfile> edited,
            @Nullable String selectedName) throws IOException {
        Path path = profilesPath();
        if (path == null) throw new IOException("Project base path is unavailable");
        EnvProfile previous = currentProfileName == null ? null : findByName(currentProfileName);
        EnvProfileStore.Snapshot saved = EnvProfileStore.save(path, expected, edited);
        profiles = List.copyOf(edited);
        EnvProfile selected = selectedName == null ? null : findByName(selectedName);
        if (selected == null) {
            clearSelection();
            try {
                clearGeneratedEnvironment(previous);
            } catch (IOException e) {
                throw new ProfileSaveException(saved, e);
            } finally {
                fireChanged();
                refreshVirtualFile(path);
            }
        } else {
            // Reflect the saved document even if the generated .env cannot be written.
            currentProfileName = selectedName;
            EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(selectedName);
            try {
                applyProfile(selected, true);
            } catch (IOException e) {
                fireChanged();
                throw new ProfileSaveException(saved, e);
            } finally {
                refreshVirtualFile(path);
            }
        }
        return saved;
    }

    private void clearGeneratedEnvironment(@Nullable EnvProfile previous) throws IOException {
        Path base = projectBasePath();
        if (previous == null || base == null) return;
        Path envFile = base.resolve(DOT_ENV_FILE);
        // Clear only the generated file we own; preserve user edits to .env.
        if (!Files.isSymbolicLink(envFile) && Files.isRegularFile(envFile)
                && readText(envFile).equals(DotEnvWriter.toDotEnvContent(previous))) {
            Files.writeString(envFile, DotEnvWriter.toEmptyDotEnvContent(), StandardCharsets.UTF_8);
            refreshVirtualFile(envFile);
        }
    }

    public static final class ProfileSaveException extends IOException {
        private final EnvProfileStore.Snapshot saved;
        private ProfileSaveException(EnvProfileStore.Snapshot saved, IOException cause) {
            super(cause instanceof EnvFileProtectionException ? cause.getMessage()
                    : "Profiles were saved, but .env could not be updated. Check project write permissions and switch the environment again.");
            this.saved = saved;
        }
        public @NotNull EnvProfileStore.Snapshot getSavedSnapshot() { return saved; }
    }

    private void clearSelection() {
        currentProfileName = null;
        EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(null);
    }

    /** 切换到指定配置，写入 .env，并持久化到 workspace。 */
    public synchronized void switchTo(@NotNull EnvProfile profile) throws IOException {
        applyProfile(profile, true);
    }

    /**
     * 根据 workspace 中记录的上次选择恢复环境。
     * 若 profile 仍存在，则同步写入 .env。
     */
    public synchronized void restorePersistedProfile() {
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
            // 启动时 .env 写入失败需提示用户，否则环境与文件不一致却无感知
            EnvNotifications.notify(project, EnvSwitcherBundle.message("notify.error", e.getMessage()), NotificationType.WARNING);
        }
    }

    private void applyProfile(@NotNull EnvProfile profile, boolean persist) throws IOException {
        Path baseDir = projectBasePath();
        if (baseDir == null) {
            throw new IOException("Project base path is unavailable");
        }
        Path envFile = baseDir.resolve(DOT_ENV_FILE);
        if (Files.isSymbolicLink(envFile)) {
            throw new EnvFileProtectionException(".env must be a regular file, not a symbolic link.");
        }
        boolean backedUp = false;
        // 非本插件生成的 .env 先移到 .env.backup，绝不覆盖已有备份
        if (Files.isRegularFile(envFile) && !DotEnvWriter.isGenerated(readText(envFile))) {
            Path backup = baseDir.resolve(".env.backup");
            if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                throw new EnvFileProtectionException("Existing .env was not generated by Env Switcher and .env.backup already exists. Move one of them and switch again.");
            }
            Files.move(envFile, backup);
            backedUp = true;
        }
        Files.writeString(envFile, DotEnvWriter.toDotEnvContent(profile), StandardCharsets.UTF_8);
        if (backedUp) {
            refreshVirtualFile(baseDir.resolve(".env.backup"));
            EnvNotifications.notify(project, EnvSwitcherBundle.message("notify.backup"), NotificationType.INFORMATION);
        }
        refreshVirtualFile(envFile);
        currentProfileName = profile.getName();
        if (persist) {
            EnvSwitcherWorkspaceState.getInstance(project).setLastProfileName(profile.getName());
        }
        fireChanged();
    }

    private static final class EnvFileProtectionException extends IOException {
        private EnvFileProtectionException(String message) { super(message); }
    }

    /** 宽松解码：手写 .env 可能不是合法 UTF-8，不应因此中断切换。 */
    private static @NotNull String readText(@NotNull Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void refreshVirtualFile(@NotNull Path path) {
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        VirtualFile file = fileSystem.findFileByNioFile(path);
        if (file != null) {
            file.refresh(true, false);
            return;
        }
        VirtualFile parent = fileSystem.findFileByNioFile(path.getParent());
        if (parent != null) {
            parent.refresh(true, false);
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
