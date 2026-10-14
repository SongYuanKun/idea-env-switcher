package io.github.ideaenvswitcher.action;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import io.github.ideaenvswitcher.EnvNotifications;
import io.github.ideaenvswitcher.EnvSwitcherBundle;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.jetbrains.annotations.NotNull;

/** 重新加载 env-profiles.json。 */
public final class ReloadProfilesAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        EnvSwitcherService service = EnvSwitcherService.getInstance(project);
        try {
            int count = service.reloadProfiles();
            EnvNotifications.notify(project, EnvSwitcherBundle.message("notify.reload.ok", count),
                    NotificationType.INFORMATION);
        } catch (Exception ex) {
            EnvNotifications.notify(project, EnvSwitcherBundle.message("notify.error", ex.getMessage()),
                    NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
