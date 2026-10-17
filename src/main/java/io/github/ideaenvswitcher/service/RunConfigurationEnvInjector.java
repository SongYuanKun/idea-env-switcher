package io.github.ideaenvswitcher.service;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.openapi.project.Project;
import io.github.ideaenvswitcher.model.EnvProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** 将当前 profile 的环境变量叠加到运行参数（运行时注入，不改写已保存的 Run Configuration）。 */
public final class RunConfigurationEnvInjector {

    private RunConfigurationEnvInjector() {
    }

    public static @Nullable EnvProfile currentProfile(@NotNull Project project) {
        if (project.isDisposed()) return null;
        EnvSwitcherService service = EnvSwitcherService.getInstance(project);
        return service.getCurrentProfile();
    }

    public static void applyProfile(
            @NotNull SimpleProgramParameters parameters,
            @Nullable EnvProfile profile) {
        Map<String, String> env = envOf(profile);
        if (env.isEmpty()) {
            return;
        }
        parameters.getEnv().putAll(env);
    }

    public static void applyProfile(
            @NotNull GeneralCommandLine commandLine,
            @Nullable EnvProfile profile) {
        Map<String, String> env = envOf(profile);
        if (env.isEmpty()) {
            return;
        }
        commandLine.withEnvironment(env);
    }

    private static @NotNull Map<String, String> envOf(@Nullable EnvProfile profile) {
        if (profile == null || profile.getEnv().isEmpty()) {
            return Map.of();
        }
        return profile.getEnv();
    }
}
