package io.github.ideaenvswitcher;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/** 插件文案 Bundle。 */
public final class EnvSwitcherBundle extends DynamicBundle {

    public static final String BUNDLE = "messages.EnvSwitcherBundle";
    private static final EnvSwitcherBundle INSTANCE = new EnvSwitcherBundle();

    private EnvSwitcherBundle() {
        super(BUNDLE);
    }

    public static @NotNull @Nls String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static @NotNull Supplier<@Nls String> messagePointer(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
