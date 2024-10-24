package quilt.internal.tasks.diff;

import quilt.internal.tasks.unpick.UnpickJarTask;

public abstract class UnpickTargetJarTask extends UnpickJarTask implements UnpickVersionsMatchConsumingTask {
    public static final String UNPICK_TARGET_JAR_TASK_NAME = "unpickTargetJar";
}