package io.github.ideaenvswitcher.service;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** 项目打开后恢复上次选中的环境，并同步 .env。 */
public final class EnvSwitcherStartupActivity implements StartupActivity, DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        EnvSwitcherService.getInstance(project).restorePersistedProfile();
    }
}
