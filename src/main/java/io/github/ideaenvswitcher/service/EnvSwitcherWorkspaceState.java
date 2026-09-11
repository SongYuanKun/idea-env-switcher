package io.github.ideaenvswitcher.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 将上次选中的 profile 持久化到 workspace（随项目工作区保存）。 */
@Service(Service.Level.PROJECT)
@State(
        name = "EnvSwitcherWorkspaceState",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class EnvSwitcherWorkspaceState
        implements PersistentStateComponent<EnvSwitcherWorkspaceState.State> {

    private final State state = new State();

    public static @NotNull EnvSwitcherWorkspaceState getInstance(@NotNull Project project) {
        return project.getService(EnvSwitcherWorkspaceState.class);
    }

    public @Nullable String getLastProfileName() {
        return state.lastProfileName;
    }

    public void setLastProfileName(@Nullable String lastProfileName) {
        state.lastProfileName = lastProfileName;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public static final class State {
        public @Nullable String lastProfileName;
    }
}
