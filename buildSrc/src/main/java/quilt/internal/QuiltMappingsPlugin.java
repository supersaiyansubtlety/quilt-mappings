package quilt.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.enigma.api.service.JarIndexerService;
import quilt.internal.decompile.Decompilers;
import quilt.internal.tasks.EnigmaProfileConsumingTask;
import quilt.internal.tasks.build.AddProposedMappingsTask;
import quilt.internal.tasks.build.BuildMappingsTinyTask;
import quilt.internal.tasks.build.CompressTinyTask;
import quilt.internal.tasks.build.DropInvalidMappingsTask;
import quilt.internal.tasks.build.GeneratePackageInfoMappingsTask;
import quilt.internal.tasks.build.InvertPerVersionMappingsTask;
import quilt.internal.tasks.build.MappingsV2JarTask;
import quilt.internal.tasks.build.MergeIntermediaryTask;
import quilt.internal.tasks.build.MergeTinyTask;
import quilt.internal.tasks.build.MergeTinyV2Task;
import quilt.internal.tasks.build.RemoveIntermediaryTask;
import quilt.internal.tasks.build.TinyJarTask;
import quilt.internal.tasks.diff.CheckTargetVersionExistsTask;
import quilt.internal.tasks.diff.CheckUnpickVersionsMatchTask;
import quilt.internal.tasks.diff.DecompileTargetTask;
import quilt.internal.tasks.diff.DownloadTargetMappingJarTask;
import quilt.internal.tasks.diff.ExtractTargetMappingJarTask;
import quilt.internal.tasks.diff.RemapTargetMinecraftJarTask;
import quilt.internal.tasks.diff.RemapTargetUnpickDefinitionsTask;
import quilt.internal.tasks.diff.TargetVersionConsumingTask;
import quilt.internal.tasks.diff.UnpickTargetJarTask;
import quilt.internal.tasks.diff.UnpickVersionsMatchConsumingTask;
import quilt.internal.tasks.jarmapping.MapJarTask;
import quilt.internal.tasks.jarmapping.MapNamedJarTask;
import quilt.internal.tasks.jarmapping.MapPerVersionMappingsJarTask;
import quilt.internal.tasks.lint.DownloadDictionaryFileTask;
import quilt.internal.tasks.lint.FindDuplicateMappingFilesTask;
import quilt.internal.tasks.lint.MappingLintTask;
import quilt.internal.tasks.mappings.AbstractEnigmaMappingsTask;
import quilt.internal.tasks.mappings.EnigmaMappingsServerTask;
import quilt.internal.tasks.mappings.EnigmaMappingsTask;
import quilt.internal.tasks.setup.CheckIntermediaryMappingsTask;
import quilt.internal.tasks.setup.DownloadIntermediaryMappingsTask;
import quilt.internal.tasks.setup.DownloadMappingsTask;
import quilt.internal.tasks.setup.DownloadMinecraftJarsTask;
import quilt.internal.tasks.setup.DownloadMinecraftLibrariesTask;
import quilt.internal.tasks.setup.DownloadVersionsManifestTask;
import quilt.internal.tasks.setup.DownloadWantedVersionManifestTask;
import quilt.internal.tasks.setup.ExtractServerJarTask;
import quilt.internal.tasks.setup.ExtractTinyMappingsTask;
import quilt.internal.tasks.setup.MergeJarsTask;
import quilt.internal.tasks.unpick.CombineUnpickDefinitionsTask;
import quilt.internal.tasks.unpick.RemapUnpickDefinitionsTask;
import quilt.internal.tasks.unpick.UnpickJarTask;
import quilt.internal.tasks.unpick.gen.OpenGlConstantUnpickGenTask;
import quilt.internal.tasks.unpick.gen.UnpickGenTask;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

import static quilt.internal.util.ProviderUtil.provideProjectDir;
import static quilt.internal.util.ProviderUtil.toOptional;

import static org.quiltmc.enigma_plugin.Arguments.SIMPLE_TYPE_FIELD_NAMES_PATH;

// TODO extract common providers, possibly convert FileConstants to providers instead of files

/**
 * TODO javadoc, including every configureEach, every configuration
 */
public abstract class QuiltMappingsPlugin implements Plugin<Project> {
    public static final String INSERT_AUTO_GENERATED_MAPPINGS_TASK_NAME = "insertAutoGeneratedMappings";
    public static final String DOWNLOAD_PER_VERSION_MAPPINGS_TASK_NAME = "downloadPerVersionMappings";
    public static final String EXTRACT_TINY_PER_VERSION_MAPPINGS_TASK_NAME = "extractTinyPerVersionMappings";
    public static final String EXTRACT_TINY_INTERMEDIARY_MAPPINGS_TASK_NAME = "extractTinyIntermediaryMappings";

    // TODO probably move to FileConstants
    public static final String TARGET_MAPPINGS_DIR = ".gradle/targets";

    public static final String CONSTANTS_SOURCE_SET_NAME = "constants";

    public static final String ARCHIVE_FILE_NAME_PREFIX =
        Constants.MAPPINGS_NAME + "-" + Constants.MAPPINGS_VERSION + "-";

    public static final String ENIGMA_RUNTIME_CONFIGURATION_NAME = "enigmaRuntime";
    public static final String DECOMPILE_CLASSPATH_CONFIGURATION_NAME = "decompileClasspath";
    public static final String PER_VERSION_MAPPINGS_CONFIGURATION_NAME = Constants.PER_VERSION_MAPPINGS_NAME;
    public static final String INTERMEDIARY_MAPPINGS_CONFIGURATION_NAME = Constants.INTERMEDIARY_MAPPINGS_NAME;

    public static final String DECOMPILE_TARGET_VINEFLOWER_TASK_NAME = "decompileTargetVineflower";
    public static final String CONSTANTS_JAR_TASK_NAME = "constantsJar";
    public static final String UNPICK_HASHED_JAR_TASK_NAME = "unpickHashedJar";
    public static final String V_2_UNMERGED_MAPPINGS_JAR_TASK_NAME = "v2UnmergedMappingsJar";
    public static final String V_2_MERGED_MAPPINGS_JAR_TASK_NAME = "v2MergedMappingsJar";
    public static final String INTERMEDIARY_V_2_MAPPINGS_JAR_TASK_NAME = "intermediaryV2MappingsJar";
    public static final String INTERMEDIARY_V_2_MERGED_MAPPINGS_JAR_TASK_NAME = "intermediaryV2MergedMappingsJar";
    public static final String BUILD_INTERMEDIARY_TASK_NAME = "buildIntermediary";
    public static final String MAPPINGS_UNPICKED_TASK_NAME = "mappingsUnpicked";
    public static final String MAPPINGS_TASK_NAME = "mappings";
    public static final String MAPPINGS_UNPICKED_SERVER_TASK_NAME = "mappingsUnpickedServer";
    public static final String MAPPINGS_SERVER_TASK_NAME = "mappingsServer";

    private static final String QUILT_MAPPINGS_PREFIX = "quilt-mappings-";

    private static final String ENIGMA_SERVER_PROP_PREFIX = "enigma_server_";
    public static final String ENIGMA_SERVER_PORT_PROP =
        ENIGMA_SERVER_PROP_PREFIX + EnigmaMappingsServerTask.PORT_OPTION;
    public static final String ENIGMA_SERVER_PASSWORD_PROP =
        ENIGMA_SERVER_PROP_PREFIX + EnigmaMappingsServerTask.PASSWORD_OPTION;
    public static final String ENIGMA_SERVER_LOG_PROP =
        ENIGMA_SERVER_PROP_PREFIX + EnigmaMappingsServerTask.LOG_OPTION;
    public static final String ENIGMA_SERVER_ARGS_PROP = ENIGMA_SERVER_PROP_PREFIX + "args";

    public static Provider<RegularFile> provideMappingsDestFile(
        Provider<Directory> destDir, String mappingsName, String fileExt
    ) {
        return destDir.map(dir -> dir.file(Constants.MINECRAFT_VERSION + "-" + mappingsName + "." + fileExt));
    }

    @Inject
    @NotNull
    public abstract ProviderFactory getProviders();

    @Override
    public void apply(@NotNull Project project) {
        final ProjectLayout projectLayout = project.getLayout();

        final ProviderFactory providers = this.getProviders();

        final ExtensionContainer extensions = project.getExtensions();

        // adds the JavaPluginExtension
        project.getPluginManager().apply(JavaBasePlugin.class);
        // has sourceSets
        final var javaExt = extensions.getByType(JavaPluginExtension.class);

        final var ext = extensions.create(QuiltMappingsExtension.EXTENSION_NAME, QuiltMappingsExtension.class, project);

        final ConfigurationContainer configurations = project.getConfigurations();

        final Configuration enigmaRuntime = configurations.create(ENIGMA_RUNTIME_CONFIGURATION_NAME);
        final Configuration decompileClasspath = configurations.create(DECOMPILE_CLASSPATH_CONFIGURATION_NAME);
        final Configuration preVersionMappings = configurations.create(PER_VERSION_MAPPINGS_CONFIGURATION_NAME);
        final Configuration intermediaryMappings = configurations.create(INTERMEDIARY_MAPPINGS_CONFIGURATION_NAME);

        final TaskContainer tasks = project.getTasks();

        final Provider<Directory> mappingsDestDir =
            provideProjectDir(project, ext.getFileConstants().cacheFilesMinecraft);

        tasks.withType(EnigmaProfileConsumingTask.class).configureEach(task -> {
            task.getEnigmaProfile().convention(ext.enigmaProfile);

            task.getEnigmaProfileConfig().convention(ext.getEnigmaProfileConfig());

            task.getSimpleTypeFieldNamesFiles().convention(
                project.provider(() -> project.files(
                    task.getEnigmaProfile().get().getServiceProfiles(JarIndexerService.TYPE).stream()
                        .flatMap(service -> service.getArgument(SIMPLE_TYPE_FIELD_NAMES_PATH).stream())
                        .map(stringOrStrings -> stringOrStrings.mapBoth(Stream::of, Collection::stream))
                        .flatMap(bothStringStreams ->
                            bothStringStreams.left().orElseGet(bothStringStreams::rightOrThrow)
                        )
                        .toList()
                ))
            );
        });

        // provide an informative error message if no profile is specified
        ext.getEnigmaProfileConfig().convention(() -> {
            throw new GradleException(
                "No enigma profile specified. " +
                    "A profile must be specified to use an EnigmaProfileConsumingTask."
            );
        });

        final var downloadVersionsManifest = tasks.register(
            DownloadVersionsManifestTask.TASK_NAME, DownloadVersionsManifestTask.class,
            task -> {
                task.getManifestFile().convention(() -> new File(
                    ext.getFileConstants().cacheFilesMinecraft,
                    "version_manifest_v2.json"
                ));
            }
        );

        final var downloadWantedVersionManifest = tasks.register(
            DownloadWantedVersionManifestTask.TASK_NAME, DownloadWantedVersionManifestTask.class,
            task -> {
                task.getManifest().convention(
                    downloadVersionsManifest.flatMap(DownloadVersionsManifestTask::getManifestFile)
                );

                task.getVersionFile().convention(() ->
                    new File(ext.getFileConstants().cacheFilesMinecraft, Constants.MINECRAFT_VERSION + ".json")
                );
            }
        );

        final var downloadMinecraftJars = tasks.register(
            DownloadMinecraftJarsTask.TASK_NAME, DownloadMinecraftJarsTask.class,
            task -> {
                task.getVersionFile().convention(
                    downloadWantedVersionManifest.flatMap(DownloadWantedVersionManifestTask::getVersionFile)
                );

                task.getClientJar().convention(() -> new File(
                    ext.getFileConstants().cacheFilesMinecraft,
                    Constants.MINECRAFT_VERSION + "-client.jar"
                ));

                task.getServerBootstrapJar().convention(() -> new File(
                    ext.getFileConstants().cacheFilesMinecraft,
                    Constants.MINECRAFT_VERSION + "-server-bootstrap.jar"
                ));
            }
        );

        final var extractServerJar = tasks.register(
            ExtractServerJarTask.TASK_NAME, ExtractServerJarTask.class,
            task -> {
                task.getServerBootstrapJar().convention(
                    downloadMinecraftJars.flatMap(DownloadMinecraftJarsTask::getServerBootstrapJar)
                );

                task.getServerJar().convention(() -> new File(
                    ext.getFileConstants().cacheFilesMinecraft,
                    Constants.MINECRAFT_VERSION + "-server.jar"
                ));
            }
        );

        final var mergeJars = tasks.register(MergeJarsTask.TASK_NAME, MergeJarsTask.class, task -> {
            task.getClientJar().convention(downloadMinecraftJars.flatMap(DownloadMinecraftJarsTask::getClientJar));
            task.getServerJar().convention(extractServerJar.flatMap(ExtractServerJarTask::getServerJar));

            // TODO see if output jars like this can all go in a directory (build/minecraftJars/?)
            final File mergedFile = project.file(Constants.MINECRAFT_VERSION + "-merged.jar");
            task.getMergedFile().convention(() -> mergedFile);
        });

        final var downloadMinecraftLibraries = tasks.register(
            DownloadMinecraftLibrariesTask.TASK_NAME, DownloadMinecraftLibrariesTask.class,
            task -> {
                task.getVersionFile().convention(
                    downloadWantedVersionManifest.flatMap(DownloadWantedVersionManifestTask::getVersionFile)
                );
                task.getLibrariesDir().convention(provideProjectDir(project, ext.getFileConstants().libraries));
            }
        );

        tasks.withType(MapJarTask.class).configureEach(task -> {
            task.getLibrariesDir().convention(
                downloadMinecraftLibraries.flatMap(DownloadMinecraftLibrariesTask::getLibrariesDir)
            );
        });

        final var downloadPerVersionMappings = tasks.register(
            DOWNLOAD_PER_VERSION_MAPPINGS_TASK_NAME, DownloadMappingsTask.class,
            task -> {
                task.getMappingsConfiguration().convention(preVersionMappings);

                task.getJarFile().convention(
                    provideMappingsDestFile(mappingsDestDir, Constants.PER_VERSION_MAPPINGS_NAME, "jar")
                );
            }
        );

        final var extractTinyPerVersionMappings = tasks.register(
            EXTRACT_TINY_PER_VERSION_MAPPINGS_TASK_NAME, ExtractTinyMappingsTask.class,
            task -> {
                task.getJarFile().convention(downloadPerVersionMappings.flatMap(DownloadMappingsTask::getJarFile));
                task.getTinyFile().convention(
                    provideMappingsDestFile(mappingsDestDir, Constants.PER_VERSION_MAPPINGS_NAME, "tiny")
                );
            }
        );

        final var invertPerVersionMappings =
            tasks.register(InvertPerVersionMappingsTask.TASK_NAME, InvertPerVersionMappingsTask.class);

        final var buildMappingsTiny = tasks.register(BuildMappingsTinyTask.TASK_NAME, BuildMappingsTinyTask.class);

        final var mapPerVersionMappingsJar = tasks.register(
            MapPerVersionMappingsJarTask.TASK_NAME, MapPerVersionMappingsJarTask.class,
            task -> {
                task.getInputJar().convention(mergeJars.flatMap(MergeJarsTask::getMergedFile));

                task.getMappingsFile().convention(
                    extractTinyPerVersionMappings.flatMap(ExtractTinyMappingsTask::getTinyFile)
                );

                task.getOutputJar().convention(() -> ext.getFileConstants().perVersionMappingsJar);
            }
        );

        final var insertAutoGeneratedMappings = tasks.register(
            INSERT_AUTO_GENERATED_MAPPINGS_TASK_NAME, AddProposedMappingsTask.class,
            task -> {
                task.getInputJar().convention(
                    mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
                );

                task.getInputMappings().convention(buildMappingsTiny.flatMap(BuildMappingsTinyTask::getOutputMappings));

                task.getOutputMappings().convention(() ->
                    new File(ext.getFileConstants().buildDir, INSERT_AUTO_GENERATED_MAPPINGS_TASK_NAME + ".tiny")
                );
            }
        );

        tasks.register(
            MergeTinyTask.TASK_NAME, MergeTinyTask.class,
            task -> {
                task.getInput().convention(buildMappingsTiny.flatMap(BuildMappingsTinyTask::getOutputMappings));

                task.getHashedTinyMappings().convention(
                    invertPerVersionMappings.flatMap(InvertPerVersionMappingsTask::getInvertedTinyFile)
                );

                task.getOutputMappings().convention(() -> new File(ext.getFileConstants().buildDir, "mappings.tiny"));
            }
        );

        tasks.register(TinyJarTask.TASK_NAME, TinyJarTask.class);

        tasks.register(CompressTinyTask.TASK_NAME, CompressTinyTask.class);

        tasks.register(DropInvalidMappingsTask.TASK_NAME, DropInvalidMappingsTask.class);

        tasks.register(OpenGlConstantUnpickGenTask.TASK_NAME, OpenGlConstantUnpickGenTask.class, task -> {
            task.getVersionFile().convention(
                downloadMinecraftLibraries.flatMap(DownloadMinecraftLibrariesTask::getVersionFile)
            );

            task.getPerVersionMappingsJar().convention(
                mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
            );

            task.getArtifactsByUrl().convention(
                downloadMinecraftLibraries.flatMap(DownloadMinecraftLibrariesTask::getArtifactsByUrl)
            );

            final Provider<Directory> buildDirProvider = provideProjectDir(project, ext.getFileConstants().buildDir);

            task.getUnpickGlStateManagerDefinitions().convention(
                buildDirProvider.map(dir -> dir.file("unpick_glstatemanager.unpick"))
            );

            task.getUnpickGlDefinitions().convention(
                buildDirProvider.map(dir -> dir.file("unpick_gl.unpick"))
            );
        });

        final var combineUnpickDefinitions = tasks.register(
            CombineUnpickDefinitionsTask.TASK_NAME, CombineUnpickDefinitionsTask.class,
            task -> {
                task.getUnpickDefinitions().from(project.provider(() ->
                    project.getTasks().withType(UnpickGenTask.class).stream()
                        .map(UnpickGenTask::getGeneratedUnpickDefinitions)
                        .toList()
                ));

                task.getOutput().convention(
                    provideProjectDir(project, ext.getFileConstants().buildDir)
                        .map(dir -> dir.file("definitions.unpick"))
                );
            }
        );

        final var constantsJar = tasks.register(CONSTANTS_JAR_TASK_NAME, Jar.class, task -> {
            task.from(javaExt.getSourceSets().named(CONSTANTS_SOURCE_SET_NAME).map(SourceSet::getOutput));

            task.getArchiveClassifier().convention(CONSTANTS_SOURCE_SET_NAME);
        });

        tasks.register(GeneratePackageInfoMappingsTask.TASK_NAME, GeneratePackageInfoMappingsTask.class);

        tasks.register(DownloadDictionaryFileTask.TASK_NAME, DownloadDictionaryFileTask.class);

        final var mappingLint = tasks.register(MappingLintTask.TASK_NAME, MappingLintTask.class);

        tasks.register(FindDuplicateMappingFilesTask.TASK_NAME, FindDuplicateMappingFilesTask.class, task -> {
            task.getMappingDirectory().convention(mappingLint.get().getMappingDirectory());
            mappingLint.get().dependsOn(task);
        });

        final var checkIntermediaryMappings =
            tasks.register(CheckIntermediaryMappingsTask.TASK_NAME, CheckIntermediaryMappingsTask.class);

        final var downloadIntermediaryMappings = tasks.register(
            DownloadIntermediaryMappingsTask.TASK_NAME, DownloadIntermediaryMappingsTask.class,
            task -> {
                // TODO eliminate this
                task.dependsOn(checkIntermediaryMappings);

                task.getMappingsConfiguration().convention(intermediaryMappings);

                task.getJarFile().convention(
                    provideMappingsDestFile(mappingsDestDir, Constants.INTERMEDIARY_MAPPINGS_NAME, "jar")
                );

                task.onlyIf(unused -> checkIntermediaryMappings.get().isPresent());
            }
        );

        final var removeIntermediary = tasks.register(RemoveIntermediaryTask.TASK_NAME, RemoveIntermediaryTask.class);

        tasks.withType(MappingsV2JarTask.class).configureEach(task -> {
            task.getUnpickMeta().convention(ext.getUnpickMeta());

            task.getUnpickDefinition().convention(
                combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput)
            );

            task.getDestinationDirectory().convention(projectLayout.getBuildDirectory().dir("libs"));
        });

        tasks.register(V_2_UNMERGED_MAPPINGS_JAR_TASK_NAME, MappingsV2JarTask.class, task -> {
            task.getMappings().convention(
                insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
            );

            task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "v2.jar");
        });

        final var intermediaryV2MappingsJar = tasks.register(
            INTERMEDIARY_V_2_MAPPINGS_JAR_TASK_NAME, MappingsV2JarTask.class,
            task -> {
                // TODO eliminate this
                task.dependsOn(checkIntermediaryMappings);

                task.getMappings().convention(removeIntermediary.flatMap(RemoveIntermediaryTask::getOutputMappings));

                task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "intermediary-v2.jar");

                task.onlyIf(unused -> checkIntermediaryMappings.get().isPresent());
            }
        );

        final var mergeTinyV2 = tasks.register(MergeTinyV2Task.TASK_NAME, MergeTinyV2Task.class, task -> {
            // TODO this used to be dependent on v2UnmergedMappingsJar, but afaict it has no effect on this task

            task.getInput().convention(
                insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
            );

            task.getHashedTinyMappings().convention(
                invertPerVersionMappings.flatMap(InvertPerVersionMappingsTask::getInvertedTinyFile)
            );

            task.getOutputMappings().convention(() -> new File(ext.getFileConstants().buildDir, "merged2.tiny"));
        });

        tasks.register(V_2_MERGED_MAPPINGS_JAR_TASK_NAME, MappingsV2JarTask.class, task -> {
            task.getMappings().convention(mergeTinyV2.flatMap(MergeTinyV2Task::getOutputMappings));

            task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "mergedv2.jar");
        });

        final var remapUnpickDefinitions = tasks.register(
            RemapUnpickDefinitionsTask.TASK_NAME, RemapUnpickDefinitionsTask.class,
            task -> {
                task.getInput().convention(combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput));

                task.getMappings().convention(mergeTinyV2.flatMap(MergeTinyV2Task::getOutputMappings));

                task.getOutput().convention(() -> new File(
                    ext.getFileConstants().buildDir,
                    Constants.PER_VERSION_MAPPINGS_NAME + "-definitions.unpick"
                ));
            }
        );

        final var unpickHashedJar = tasks.register(UNPICK_HASHED_JAR_TASK_NAME, UnpickJarTask.class, task -> {
            task.getInputFile().convention(
                mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
            );

            task.getUnpickDefinition().convention(
                remapUnpickDefinitions.flatMap(RemapUnpickDefinitionsTask::getOutput)
            );

            task.getUnpickConstantsJar().set(constantsJar.flatMap(Jar::getArchiveFile));

            task.getOutputFile().convention(() -> ext.getFileConstants().unpickedJar);
        });

        tasks.register(MapNamedJarTask.TASK_NAME, MapNamedJarTask.class, task -> {
            task.getInputJar().convention(unpickHashedJar.flatMap(UnpickJarTask::getOutputFile));

            task.getMappingsFile().convention(
                insertAutoGeneratedMappings.flatMap(AddProposedMappingsTask::getOutputMappings)
            );

            task.getOutputJar().convention(() -> ext.getFileConstants().namedJar);
        });

        tasks.withType(AbstractEnigmaMappingsTask.class).configureEach(task -> {
            task.getMappingsDir().convention(ext.getMappingsDir());

            task.classpath(enigmaRuntime);

            task.jvmArgs("-Xmx2048m");
        });

        tasks.register(MAPPINGS_UNPICKED_TASK_NAME, EnigmaMappingsTask.class, task -> {
            task.getJarToMap().convention(unpickHashedJar.flatMap(UnpickJarTask::getOutputFile));
        });

        tasks.register(MAPPINGS_TASK_NAME, EnigmaMappingsTask.class, task -> {
            task.getJarToMap().convention(mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar));
        });

        tasks.withType(EnigmaMappingsServerTask.class).configureEach(task -> {
            task.getPort().convention(
                providers.gradleProperty(ENIGMA_SERVER_PORT_PROP)
            );

            task.getPassword().convention(
                providers.gradleProperty(ENIGMA_SERVER_PASSWORD_PROP)
            );

            task.getLog().convention(
                providers.gradleProperty(ENIGMA_SERVER_LOG_PROP)
                    .map(projectLayout.getProjectDirectory()::file)
                    .orElse(projectLayout.getBuildDirectory().file("logs/server.log"))
            );

            toOptional(
                providers.gradleProperty(ENIGMA_SERVER_ARGS_PROP).map(args -> args.split(" "))
            ).ifPresent(task::args);
        });

        tasks.register(MAPPINGS_UNPICKED_SERVER_TASK_NAME, EnigmaMappingsServerTask.class, task -> {
            task.getJarToMap().convention(unpickHashedJar.flatMap(UnpickJarTask::getOutputFile));
        });

        tasks.register(MAPPINGS_SERVER_TASK_NAME, EnigmaMappingsServerTask.class, task -> {
            task.getJarToMap().convention(mapPerVersionMappingsJar.flatMap(MapJarTask::getOutputJar));
        });

        final var extractTinyIntermediaryMappings = tasks.register(
            EXTRACT_TINY_INTERMEDIARY_MAPPINGS_TASK_NAME, ExtractTinyMappingsTask.class,
            task -> {
                task.getJarFile().convention(downloadIntermediaryMappings.flatMap(DownloadMappingsTask::getJarFile));
                task.getTinyFile().convention(
                    provideMappingsDestFile(mappingsDestDir, Constants.INTERMEDIARY_MAPPINGS_NAME, "tiny")
                );
            }
        );

        final var mergeIntermediary = tasks.register(
            MergeIntermediaryTask.TASK_NAME, MergeIntermediaryTask.class,
            task -> {
                // TODO eliminate this
                task.dependsOn(checkIntermediaryMappings);

                task.getInput().convention(
                    extractTinyIntermediaryMappings.flatMap(ExtractTinyMappingsTask::getTinyFile)
                );

                task.getMergedTinyMappings().convention(mergeTinyV2.flatMap(MergeTinyV2Task::getOutputMappings));

                task.getOutputMappings().convention(() ->
                    new File(ext.getFileConstants().buildDir, "mappings-intermediaryMerged.tiny")
                );

                task.onlyIf(unused -> checkIntermediaryMappings.get().isPresent());
            }
        );

        final var intermediaryV2MergedMappingsJar = tasks.register(
            INTERMEDIARY_V_2_MERGED_MAPPINGS_JAR_TASK_NAME, MappingsV2JarTask.class,
            task -> {
                // TODO eliminate this
                task.dependsOn(checkIntermediaryMappings);

                task.getArchiveFileName().convention(ARCHIVE_FILE_NAME_PREFIX + "intermediary-mergedv2.jar");

                task.getMappings().convention(mergeIntermediary.flatMap(MergeIntermediaryTask::getOutputMappings));

                task.onlyIf(unused -> checkIntermediaryMappings.get().isPresent());
            }
        );

        tasks.register(BUILD_INTERMEDIARY_TASK_NAME, DefaultTask.class, task -> {
            task.dependsOn(intermediaryV2MappingsJar, intermediaryV2MergedMappingsJar);
        });

        final var checkTargetVersionExists = tasks.register(
            CheckTargetVersionExistsTask.TASK_NAME, CheckTargetVersionExistsTask.class,
            task -> {
                task.outputsNeverUpToDate();

                task.getMetaFile().convention(() -> new File(
                    ext.getFileConstants().cacheFilesMinecraft,
                    QUILT_MAPPINGS_PREFIX + Constants.MINECRAFT_VERSION + ".json"
                ));
            }
        );

        tasks.withType(TargetVersionConsumingTask.class).configureEach(task -> {
            // TODO temporary, until CheckTargetVersionExistsTask is converted to a BuildService
            task.dependsOn(checkTargetVersionExists);

            task.getTargetVersion().convention(
                checkTargetVersionExists.flatMap(CheckTargetVersionExistsTask::getTargetVersion)
            );

            task.onlyIf(unused -> task.getTargetVersion().isPresent());
        });

        final var downloadTargetMappingsJar = tasks.register(
            DownloadTargetMappingJarTask.TASK_NAME, DownloadTargetMappingJarTask.class,
            task -> {
                task.getTargetUnpickConstantsFile().convention(task.provideVersionedProjectFile(version ->
                    Path.of(TARGET_MAPPINGS_DIR, QUILT_MAPPINGS_PREFIX + version + "-constants.jar")
                ));

                task.getTargetJar().convention(task.provideVersionedProjectFile(version ->
                    Path.of(QuiltMappingsPlugin.TARGET_MAPPINGS_DIR, "quilt-mappings-" + version + "-v2.jar")
                ));
            }
        );

        final var extractTargetMappingsJar = tasks.register(
            ExtractTargetMappingJarTask.TASK_NAME, ExtractTargetMappingJarTask.class,
            task -> {
                task.getTargetJar().convention(
                    downloadTargetMappingsJar.flatMap(DownloadTargetMappingJarTask::getTargetJar)
                );
                task.getExtractionDest().convention(task.provideVersionedProjectDir(version ->
                    Path.of(QuiltMappingsPlugin.TARGET_MAPPINGS_DIR, "quilt-mappings-" + version)
                ));
            }
        );

        final var checkUnpickVersionsMatch = tasks.register(
            CheckUnpickVersionsMatchTask.TASK_NAME, CheckUnpickVersionsMatchTask.class,
            task -> {
                task.getUnpickMeta().convention(
                    extractTargetMappingsJar.flatMap(ExtractTargetMappingJarTask::getExtractionDest)
                        .map(dest -> dest.file(MappingsV2JarTask.JAR_UNPICK_META_PATH))
                );
            }
        );

        tasks.withType(UnpickVersionsMatchConsumingTask.class).configureEach(task -> {
            // TODO temporary, until CheckUnpickVersionsMatchTask is converted to a BuildService
            task.dependsOn(checkUnpickVersionsMatch);

            task.getUnpickVersionsMatch().convention(
                checkUnpickVersionsMatch.flatMap(CheckUnpickVersionsMatchTask::isMatch)
            );

            task.onlyIf(unused -> task.getUnpickVersionsMatch().getOrElse(false));
        });

        final var remapTargetUnpickDefinitions = tasks.register(
            RemapTargetUnpickDefinitionsTask.TASK_NAME, RemapTargetUnpickDefinitionsTask.class,
            task -> {
                task.getInput().convention(
                    extractTargetMappingsJar.flatMap(ExtractTargetMappingJarTask::getExtractionDest)
                        .map(dest -> dest.file(MappingsV2JarTask.JAR_UNPICK_DEFINITION_PATH))
                );

                task.getMappings().convention(
                    extractTargetMappingsJar.flatMap(ExtractTargetMappingJarTask::getExtractionDest)
                        .map(dest -> dest.file(MappingsV2JarTask.JAR_MAPPINGS_PATH))
                );

                task.getOutput().convention(task.provideVersionedProjectFile(version ->
                    Path.of(TARGET_MAPPINGS_DIR, QUILT_MAPPINGS_PREFIX + version + "remapped-unpick.unpick")
                ));
            }
        );

        final var unpickTargetJar = tasks.register(UnpickTargetJarTask.TASK_NAME, UnpickTargetJarTask.class, task -> {
            task.getInputFile().convention(
                mapPerVersionMappingsJar.flatMap(MapPerVersionMappingsJarTask::getOutputJar)
            );

            task.getUnpickDefinition().convention(
                remapTargetUnpickDefinitions.flatMap(RemapTargetUnpickDefinitionsTask::getOutput)
            );

            task.getUnpickConstantsJar().convention(
                downloadTargetMappingsJar.flatMap(DownloadTargetMappingJarTask::getTargetUnpickConstantsFile)
            );

            task.getOutputFile().convention(task.provideVersionedProjectFile(version ->
                Path.of(TARGET_MAPPINGS_DIR, QUILT_MAPPINGS_PREFIX + version + "-unpicked.jar")
            ));
        });

        final var remapTargetMinecraftJar = tasks.register(
            RemapTargetMinecraftJarTask.TASK_NAME, RemapTargetMinecraftJarTask.class,
            task -> {
                // TODO temporary until CheckTargetVersionExists and CheckUnpickVersionsMatchTask
                //  are converted to BuildService's
                task.dependsOn(unpickTargetJar);

                task.getInputJar().convention(unpickTargetJar.flatMap(UnpickTargetJarTask::getOutputFile));

                task.getMappingsFile().convention(
                    extractTargetMappingsJar.flatMap(ExtractTargetMappingJarTask::getExtractionDest)
                        .map(dest -> dest.dir("mappings").file("mappings.tiny"))
                );

                task.getOutputJar().convention(task.provideVersionedProjectFile(version ->
                    Path.of(TARGET_MAPPINGS_DIR, QUILT_MAPPINGS_PREFIX + version + "-named.jar")
                ));
            }
        );

        // TODO there was a non-fatal exception error logged by this a few times that I can't reproduce
        //  It mentioned jetbrains, so it might have just been intellij being iffy
        //  It was a FileSystemAlreadyExistsException
        tasks.register(DECOMPILE_TARGET_VINEFLOWER_TASK_NAME, DecompileTargetTask.class, task -> {
            task.getDecompiler().convention(Decompilers.VINEFLOWER);

            task.getNamespace().convention("named");

            task.getInput().convention(remapTargetMinecraftJar.flatMap(RemapTargetMinecraftJarTask::getOutputJar));

            task.getLibraries().convention(project.files(decompileClasspath));

            task.getTargetMappingsFile().convention(
                extractTargetMappingsJar.flatMap(ExtractTargetMappingJarTask::getExtractionDest)
                    .map(dest -> dest.dir("mappings").file("mappings.tiny"))
            );

            task.getOutput().convention(() -> project.file("namedTargetSrc"));
        });
    }
}
