package quilt.internal.plugin;

import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jetbrains.annotations.NotNull;
import quilt.internal.tasks.jarmapping.MapPerVersionMappingsJarTask;
import quilt.internal.tasks.lint.Checker;
import quilt.internal.tasks.lint.DownloadDictionaryFileTask;
import quilt.internal.tasks.lint.FindDuplicateMappingFilesTask;
import quilt.internal.tasks.lint.MappingLintTask;

public abstract class MappingVerificationPlugin implements MappingsProjectPlugin {
    @Override
    public void apply(@NotNull Project project) {
        // apply required plugins and save their registered objects
        final PluginContainer plugins = project.getPlugins();

        // adds check task
        plugins.apply(LifecycleBasePlugin.class);

        // configures MappingsDirConsumingTasks (findDuplicateMappingFiles, mappingLint)
        plugins.apply(QuiltMappingsBasePlugin.class);

        final MapMinecraftJarsPlugin.Tasks mapMinecraftJarsTasks =
            plugins.apply(MapMinecraftJarsPlugin.class).getTasks();
        final TaskProvider<MapPerVersionMappingsJarTask> mapPerVersionMappingsJar =
            mapMinecraftJarsTasks.mapPerVersionMappingsJar();

        // register this plugin's tasks
        final TaskContainer tasks = project.getTasks();

        final var downloadDictionaryFile = tasks.register(
            DownloadDictionaryFileTask.DOWNLOAD_DICTIONARY_FILE_TASK_NAME,
            DownloadDictionaryFileTask.class,
            task -> {
                // configuration is in build.gradle because it depends on an external url that is prone to change
                // TODO the output file configuration could be moved here if its name didn't contain the revision

                this.provideDefaultError(
                    task.getUrl(),
                    "No url specified. " +
                        "A url must be specified to use " + task.getName() + " or any task that depends on it."
                );

                this.provideDefaultError(
                    task.getDest(),
                    "No dest specified." +
                        "An dest must be specified to use " + task.getName() + " or any task that depends on it."
                );
            }
        );

        final var findDuplicateMappingFiles = tasks.register(
            FindDuplicateMappingFilesTask.FIND_DUPLICATE_MAPPING_FILES_TASK_NAME,
            FindDuplicateMappingFilesTask.class
        );

        final var mappingLint = tasks.register(
            MappingLintTask.MAPPING_LINT_TASK_NAME,
            MappingLintTask.class,
            task -> {
                // this does mappings verification but has no output to depend on
                task.dependsOn(findDuplicateMappingFiles);

                task.getJarFile().convention(
                    mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
                );

                task.getCheckers().addAll(Checker.DEFAULT_CHECKERS);

                task.getDictionaryFile().convention(
                    downloadDictionaryFile.flatMap(DownloadDictionaryFileTask::getDest)
                );
            }
        );

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(mappingLint));
    }
}
