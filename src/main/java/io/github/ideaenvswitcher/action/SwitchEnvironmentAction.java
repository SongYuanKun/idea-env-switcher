package io.github.ideaenvswitcher.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import io.github.ideaenvswitcher.EnvSwitcherBundle;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 弹出环境列表并切换当前配置。 */
public final class SwitchEnvironmentAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        showSwitcher(project, e.getDataContext());
    }

    /** 供状态栏等入口复用。 */
    public static void showSwitcher(@NotNull Project project) {
        showSwitcher(project, SimpleDataContext.getProjectContext(project));
    }

    private static void showSwitcher(@NotNull Project project, @NotNull com.intellij.openapi.actionSystem.DataContext dataContext) {
        EnvSwitcherService service = EnvSwitcherService.getInstance(project);
        List<EnvProfile> profiles = service.getProfiles();
        if (profiles.isEmpty()) {
            showNotification(project, EnvSwitcherBundle.message("dialog.empty"), NotificationType.WARNING);
            return;
        }

        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>(EnvSwitcherBundle.message("dialog.title"), profiles) {
                    @Override
                    public @NotNull String getTextFor(EnvProfile value) {
                        String description = value.getDescription();
                        if (description == null || description.isBlank()) {
                            return value.getName();
                        }
                        return value.getName() + " — " + description;
                    }

                    @Override
                    public @Nullable PopupStep<?> onChosen(EnvProfile selectedValue, boolean finalChoice) {
                        if (finalChoice) {
                            try {
                                service.switchTo(selectedValue);
                                showNotification(project,
                                        EnvSwitcherBundle.message("notify.switched", selectedValue.getName()),
                                        NotificationType.INFORMATION);
                            } catch (Exception ex) {
                                showNotification(project,
                                        EnvSwitcherBundle.message("notify.error", ex.getMessage()),
                                        NotificationType.ERROR);
                            }
                        }
                        return FINAL_CHOICE;
                    }
                });
        popup.showInBestPositionFor(dataContext);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull com.intellij.openapi.actionSystem.ActionUpdateThread getActionUpdateThread() {
        return com.intellij.openapi.actionSystem.ActionUpdateThread.BGT;
    }

    private static void showNotification(
            @NotNull Project project,
            @NotNull String content,
            @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Env Switcher")
                .createNotification(content, type)
                .notify(project);
    }
}
