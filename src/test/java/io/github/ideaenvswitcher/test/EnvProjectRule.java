package io.github.ideaenvswitcher.test;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Finish project indexing and EDT callbacks before ProjectRule disposes its fixture. */
public final class EnvProjectRule implements TestRule {
    private final ProjectRule delegate = new ProjectRule();

    public Project getProject() { return delegate.getProject(); }

    @Override public Statement apply(Statement base, Description description) {
        return delegate.apply(new Statement() {
            @Override public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    Project project = delegate.getProjectIfOpened();
                    if (project != null && !project.isDisposed()) {
                        EdtTestUtil.runInEdtAndWait(() -> {
                            IndexingTestUtil.waitUntilIndexesAreReady(project);
                            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
                        });
                    }
                }
            }
        }, description);
    }
}
