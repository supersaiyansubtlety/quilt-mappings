package quilt.internal.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import quilt.internal.Constants;
import quilt.internal.QuiltMappingsExtension;
import quilt.internal.tasks.build.AddProposedMappingsTask;
import quilt.internal.tasks.build.InvertPerVersionMappingsTask;
import quilt.internal.tasks.build.MappingsV2JarTask;
import quilt.internal.tasks.build.MergeTinyV2Task;
import quilt.internal.tasks.jarmapping.MapNamedJarTask;
import quilt.internal.tasks.jarmapping.MapPerVersionMappingsJarTask;
import quilt.internal.tasks.setup.ConstantsJarTask;
import quilt.internal.tasks.setup.DownloadMinecraftLibrariesTask;
import quilt.internal.tasks.unpick.CombineUnpickDefinitionsTask;
import quilt.internal.tasks.unpick.RemapUnpickDefinitionsTask;
import quilt.internal.tasks.unpick.UnpickJarTask;
import quilt.internal.tasks.unpick.gen.OpenGlConstantUnpickGenTask;
import quilt.internal.tasks.unpick.gen.UnpickGenTask;

import static quilt.internal.plugin.QuiltMappingsBasePlugin.ARCHIVE_FILE_NAME_PREFIX;

/**
 * Note that intermediary v2 tasks are registered in IntermediaryPlugin
 */
public abstract class MapV2Plugin implements MappingsProjectPlugin {
    public static final String UNPICK_CONFIGURATION_NAME = Constants.UNPICK_NAME;

    @Nullable
    private Tasks tasks;

    public Tasks getTasks() {
        return this.requireNonNullTasks(this.tasks);
    }

    @Override
    public void apply(@NotNull Project project) {
        final Configuration unpick = project.getConfigurations().create(UNPICK_CONFIGURATION_NAME);

        // apply required plugins and save their registered objects
        final PluginContainer plugins = project.getPlugins();

        final QuiltMappingsExtension ext = plugins.apply(QuiltMappingsBasePlugin.class).getExt();

        final MinecraftJarsPlugin.Tasks minecraftJarsTasks =
            plugins.apply(MinecraftJarsPlugin.class).getTasks();
        final TaskProvider<DownloadMinecraftLibrariesTask> downloadMinecraftLibraries =
            minecraftJarsTasks.downloadMinecraftLibraries();

        final MapMinecraftJarsPlugin.Tasks mapMinecraftJarsTasks =
            plugins.apply(MapMinecraftJarsPlugin.class).getTasks();
        final TaskProvider<MapPerVersionMappingsJarTask> mapPerVersionMappingsJar =
            mapMinecraftJarsTasks.mapPerVersionMappingsJar();
        // final TaskProvider<MergeTinyV2Task> mergeTinyV2 =
        //     mapMinecraftJarsTasks.mergeTinyV2();
        final TaskProvider<InvertPerVersionMappingsTask> invertPerVersionMappings =
            mapMinecraftJarsTasks.invertPerVersionMappings();
        final TaskProvider<AddProposedMappingsTask> insertAutoGeneratedMappings =
            mapMinecraftJarsTasks.insertAutoGeneratedMappings();

        // register this plugin's tasks
        final TaskContainer tasks = project.getTasks();

        final var mergeTinyV2 = tasks.register(
            MergeTinyV2Task.MERGE_TINY_V_2_TASK_NAME,
            MergeTinyV2Task.class,
            task -> {
                // TODO this used to be dependent on v2UnmergedMappingsJar, but afaict it has no effect on this task

                task.getInput().convention(
                    insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
                );

                task.getHashedTinyMappings().convention(
                    invertPerVersionMappings.flatMap(InvertPerVersionMappingsTask::getInvertedTinyFile)
                );

                task.getOutputMappings().convention(
                    this.getMappingsDir().map(dir -> dir.file("merged2.tiny"))
                );
            }
        );

        tasks.register(
            OpenGlConstantUnpickGenTask.OPEN_GL_UNPICK_GEN_TASK_NAME,
            OpenGlConstantUnpickGenTask.class,
            task -> {
                task.getPerVersionMappingsJar().convention(
                    mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
                );

                task.getArtifactsByName().convention(
                    downloadMinecraftLibraries.flatMap(DownloadMinecraftLibrariesTask::getArtifactsByName)
                );

                task.getUnpickGlStateManagerDefinitions().convention(
                    this.getMappingsDir().map(dir -> dir.file("unpick_glstatemanager.unpick"))
                );

                task.getUnpickGlDefinitions().convention(
                    this.getMappingsDir().map(dir -> dir.file("unpick_gl.unpick"))
                );
            }
        );

        final var combineUnpickDefinitions = tasks.register(
            CombineUnpickDefinitionsTask.COMBINE_UNPICK_DEFINITIONS_TASK_NAME,
            CombineUnpickDefinitionsTask.class,
            task -> {
                task.getUnpickDefinitions().from(project.getTasks().withType(UnpickGenTask.class));

                task.getOutput().convention(
                    this.getMappingsDir().map(dir -> dir.file("definitions.unpick"))
                );
            }
        );

        final var remapUnpickDefinitions = tasks.register(
            RemapUnpickDefinitionsTask.REMAP_UNPICK_DEFINITIONS_TASK_NAME,
            RemapUnpickDefinitionsTask.class,
            task -> {
                task.getInput().convention(combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput));

                task.getMappings().convention(mergeTinyV2.flatMap(MergeTinyV2Task::getOutputMappings));

                task.getOutput().convention(this.getMappingsDir().map(dir ->
                    dir.file(Constants.PER_VERSION_MAPPINGS_NAME + "-definitions.unpick")
                ));
            }
        );

        // constants are configured in build.gradle because they're from a project source set
        final var constantsJar = tasks.register(ConstantsJarTask.CONSTANTS_JAR_TASK_NAME, ConstantsJarTask.class);

        tasks.withType(UnpickJarTask.class).configureEach(task -> {
            task.classpath(unpick);

            task.getInputFile().convention(
                mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
            );

            task.getDecompileClasspathFiles().from(
                downloadMinecraftLibraries.flatMap(DownloadMinecraftLibrariesTask::getLibrariesDir)
                    .map(Directory::getAsFileTree)
                    .map(FileTree::getFiles)
            );
        });

        final var unpickHashedJar = tasks.register(
            UnpickJarTask.UNPICK_HASHED_JAR_TASK_NAME,
            UnpickJarTask.class,
            task -> {
                task.getUnpickDefinition().convention(
                    remapUnpickDefinitions.flatMap(RemapUnpickDefinitionsTask::getOutput)
                );

                task.getUnpickConstantsJar().set(constantsJar.flatMap(ConstantsJarTask::getArchiveFile));

                // TODO move this and other jars that are directly in the project dir to some sub dir
                task.getOutputFile().convention(this.getProjectDir().file(
                    Constants.MINECRAFT_VERSION + "-" + Constants.PER_VERSION_MAPPINGS_NAME + "-unpicked.jar"
                ));
            }
        );

        final var mapNamedJar = tasks.register(
            MapNamedJarTask.MAP_NAMED_JAR_TASK_NAME,
            MapNamedJarTask.class,
            task -> {
                task.getInputJar().convention(unpickHashedJar.flatMap(UnpickJarTask::getOutputFile));

                task.getMappingsFile().convention(
                    insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
                );

                task.getOutputJar().convention(
                    this.getProjectDir().file(Constants.MINECRAFT_VERSION + "-named.jar")
                );
            }
        );

        tasks.withType(MappingsV2JarTask.class).configureEach(task -> {
            task.getUnpickMeta().convention(ext.getUnpickMeta());

            task.getUnpickDefinition().convention(
                combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput)
            );

            task.getDestinationDirectory().convention(this.getLibsDir());
        });

        {
            final var v2UnmergedMappingsJar = tasks.register(
                MappingsV2JarTask.V_2_UNMERGED_MAPPINGS_JAR_TASK_NAME,
                MappingsV2JarTask.class,
                ext.getUnpickVersion()
            );
            v2UnmergedMappingsJar.configure(task -> {
                task.getMappings().convention(
                    insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
                );

                task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "-v2.jar");
            });
        }

        final var v2MergedMappingsJar = tasks.register(
            MappingsV2JarTask.V_2_MERGED_MAPPINGS_JAR_TASK_NAME,
            MappingsV2JarTask.class,
            ext.getUnpickVersion()
        );
        v2MergedMappingsJar.configure(task -> {
            task.getMappings().convention(mergeTinyV2.flatMap(MergeTinyV2Task::getOutputMappings));

            task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "-mergedv2.jar");
        });

        this.tasks = new Tasks(mergeTinyV2, unpickHashedJar, mapNamedJar);
    }

    public record Tasks(
        TaskProvider<MergeTinyV2Task> mergeTinyV2,
        TaskProvider<UnpickJarTask> unpickHashedJar,
        TaskProvider<MapNamedJarTask> mapNamedJar
    ) { }
}