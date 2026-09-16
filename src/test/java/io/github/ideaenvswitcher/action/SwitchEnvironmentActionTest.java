package io.github.ideaenvswitcher.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.UiInterceptors;
import io.github.ideaenvswitcher.service.EnvSwitcherService;

import java.nio.file.Files;
import java.nio.file.Path;

public class SwitchEnvironmentActionTest extends BasePlatformTestCase {

    public void testShowsProfilePopupWithoutFocusedComponent() throws Exception {
        Path profilesFile = Path.of(getProject().getBasePath()).resolve("env-profiles.json");
        Files.createDirectories(profilesFile.getParent());
        Files.writeString(profilesFile, """
                {
                  "profiles": [
                    {"name": "dev", "env": {"APP_ENV": "dev"}},
                    {"name": "staging", "env": {"APP_ENV": "staging"}}
                  ]
                }
                """);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(profilesFile);
        assertEquals(2, EnvSwitcherService.getInstance(getProject()).reloadProfiles());

        Ref<ListPopup> popupRef = new Ref<>();
        UiInterceptors.register(new UiInterceptors.UiInterceptor<>(ListPopup.class) {
            @Override
            protected void doIntercept(ListPopup popup) {
                popupRef.set(popup);
            }
        });
        try {
            ApplicationManager.getApplication().invokeAndWait(
                    () -> SwitchEnvironmentAction.showSwitcher(getProject()));

            assertNotNull("environment chooser should be presented", popupRef.get());
            assertEquals(2, popupRef.get().getListStep().getValues().size());
        } finally {
            UiInterceptors.clear();
        }
    }

}
