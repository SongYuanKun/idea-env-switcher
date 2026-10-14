package io.github.ideaenvswitcher.action;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import io.github.ideaenvswitcher.EnvNotifications;
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
        showSwitcher(project);
    }

    /** 供状态栏等入口复用。 */
    public static void showSwitcher(@NotNull Project project) {
        EnvSwitcherService service = EnvSwitcherService.getInstance(project);
        List<EnvProfile> profiles = service.getProfiles();
        if (profiles.isEmpty()) {
            EnvNotifications.notify(project, EnvSwitcherBundle.message("dialog.empty"), NotificationType.WARNING);
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
                                EnvNotifications.notify(project,
                                        EnvSwitcherBundle.message("notify.switched", selectedValue.getName()),
                                        NotificationType.INFORMATION);
                            } catch (Exception ex) {
                                EnvNotifications.notify(project,
                                        EnvSwitcherBundle.message("notify.error", ex.getMessage()),
                                        NotificationType.ERROR);
                            }
                        }
                        return FINAL_CHOICE;
                    }
                });
        popup.showCenteredInCurrentWindow(project);
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
