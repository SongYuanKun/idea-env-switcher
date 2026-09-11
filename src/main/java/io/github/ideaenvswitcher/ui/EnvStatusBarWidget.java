package io.github.ideaenvswitcher.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import io.github.ideaenvswitcher.EnvSwitcherBundle;
import io.github.ideaenvswitcher.action.SwitchEnvironmentAction;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.event.MouseEvent;

/** 状态栏展示当前环境，点击触发切换。 */
public final class EnvStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {

    private final Project project;
    private final EnvSwitcherService service;
    private final Runnable listener = this::refresh;
    private @Nullable StatusBar statusBar;

    public EnvStatusBarWidget(@NotNull Project project) {
        this.project = project;
        this.service = EnvSwitcherService.getInstance(project);
        this.service.addChangeListener(listener);
        Disposer.register(this, () -> service.removeChangeListener(listener));
    }

    @Override
    public @NotNull String ID() {
        return EnvStatusBarWidgetFactory.ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public @NotNull String getText() {
        String name = service.getCurrentProfileName();
        if (name == null || name.isBlank()) {
            return EnvSwitcherBundle.message("status.no.profile");
        }
        return EnvSwitcherBundle.message("status.current", name);
    }

    @Override
    public float getAlignment() {
        return Component.CENTER_ALIGNMENT;
    }

    @Override
    public @Nullable String getTooltipText() {
        return EnvSwitcherBundle.message("switch.environment");
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return event -> SwitchEnvironmentAction.showSwitcher(project);
    }

    @Override
    public void dispose() {
        // Disposer 已清理 listener
    }

    private void refresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            StatusBar bar = statusBar != null
                    ? statusBar
                    : WindowManager.getInstance().getStatusBar(project);
            if (bar != null) {
                bar.updateWidget(ID());
            }
        });
    }
}
