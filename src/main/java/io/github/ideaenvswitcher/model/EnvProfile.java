package io.github.ideaenvswitcher.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 单个命名环境配置。 */
public final class EnvProfile {

    private final @NotNull String name;
    private final @Nullable String description;
    private final @NotNull Map<String, String> env;

    public EnvProfile(
            @NotNull String name,
            @Nullable String description,
            @NotNull Map<String, String> env) {
        this.name = name;
        this.description = description;
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
    }

    public @NotNull String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @NotNull Map<String, String> getEnv() {
        return env;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnvProfile that)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
