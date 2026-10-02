package io.github.ideaenvswitcher.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import io.github.ideaenvswitcher.ui.EnvSwitcherConfigurable;
import org.jetbrains.annotations.NotNull;

public final class ManageProfilesAction extends AnAction implements DumbAware {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() != null) ShowSettingsUtil.getInstance().showSettingsDialog(event.getProject(), EnvSwitcherConfigurable.class);
    }
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
}
