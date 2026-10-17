package io.github.ideaenvswitcher;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** 统一项目通知入口。 */
public final class EnvNotifications {
    public static final String GROUP_ID = "Env Switcher";

    private EnvNotifications() { }

    public static void notify(@NotNull Project project, @NotNull String content, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
                .createNotification(content, type).notify(project);
    }
}
