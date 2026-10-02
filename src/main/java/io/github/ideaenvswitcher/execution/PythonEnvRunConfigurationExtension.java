package io.github.ideaenvswitcher.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunnerSettings;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonRunConfigurationExtension;
import io.github.ideaenvswitcher.service.RunConfigurationEnvInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loaded only with PythonCore; patches local Python launch commands at runtime. */
public final class PythonEnvRunConfigurationExtension extends PythonRunConfigurationExtension {
    @Override public boolean isApplicableFor(@NotNull AbstractPythonRunConfiguration<?> configuration) { return true; }

    @Override public boolean isEnabledFor(@NotNull AbstractPythonRunConfiguration<?> configuration,
                                           @Nullable RunnerSettings settings) { return true; }

    @Override protected void patchCommandLine(@NotNull AbstractPythonRunConfiguration<?> configuration,
                                               @Nullable RunnerSettings settings,
                                               @NotNull GeneralCommandLine commandLine,
                                               @NotNull String runnerId) {
        RunConfigurationEnvInjector.applyProfile(commandLine, RunConfigurationEnvInjector.currentProfile(configuration.getProject()));
    }
}
