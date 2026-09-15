package io.github.ideaenvswitcher.execution;

import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.project.Project;
import io.github.ideaenvswitcher.model.EnvProfile;
import io.github.ideaenvswitcher.service.EnvSwitcherService;
import io.github.ideaenvswitcher.service.RunConfigurationEnvInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 在 Java 系 Run Configuration 启动时注入当前 Env Switcher profile。 */
public final class EnvRunConfigurationExtension extends RunConfigurationExtension {

    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return true;
    }

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(
            @NotNull T configuration,
            @NotNull JavaParameters params,
            @Nullable RunnerSettings runnerSettings) {
        RunConfigurationEnvInjector.applyProfile(params, currentProfile(configuration.getProject()));
    }

    @Override
    protected void patchCommandLine(
            @NotNull RunConfigurationBase configuration,
            @Nullable RunnerSettings runnerSettings,
            @NotNull GeneralCommandLine cmdLine,
            @NotNull String runnerId) {
        RunConfigurationEnvInjector.applyProfile(cmdLine, currentProfile(configuration.getProject()));
    }

    private static @Nullable EnvProfile currentProfile(@NotNull Project project) {
        if (project.isDisposed()) {
            return null;
        }
        EnvSwitcherService service = EnvSwitcherService.getInstance(project);
        String name = service.getCurrentProfileName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return service.findByName(name);
    }
}
