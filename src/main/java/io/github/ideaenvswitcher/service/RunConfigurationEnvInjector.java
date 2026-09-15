package io.github.ideaenvswitcher.service;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleProgramParameters;
import io.github.ideaenvswitcher.model.EnvProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将当前 profile 的环境变量叠加到运行参数（运行时注入，不改写已保存的 Run Configuration）。 */
public final class RunConfigurationEnvInjector {

    private RunConfigurationEnvInjector() {
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

    /** 叠加 profile 环境变量：同名键以 profile 为准，保留其余已有键。 */
    public static @NotNull Map<String, String> merge(
            @NotNull Map<String, String> existing,
            @Nullable EnvProfile profile) {
        Map<String, String> merged = new LinkedHashMap<>(existing);
        Map<String, String> env = envOf(profile);
        if (!env.isEmpty()) {
            merged.putAll(env);
        }
        return merged;
    }

    private static @NotNull Map<String, String> envOf(@Nullable EnvProfile profile) {
        if (profile == null || profile.getEnv().isEmpty()) {
            return Map.of();
        }
        return profile.getEnv();
    }
}
