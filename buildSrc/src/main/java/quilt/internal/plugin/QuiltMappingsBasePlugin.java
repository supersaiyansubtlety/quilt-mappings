package quilt.internal.plugin;

import org.gradle.api.Project;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.enigma.api.service.JarIndexerService;
import quilt.internal.Constants;
import quilt.internal.QuiltMappingsExtension;
import quilt.internal.tasks.EnigmaProfileConsumingTask;
import quilt.internal.tasks.MappingsDirConsumingTask;
import quilt.internal.tasks.mappings.MappingsDirOutputtingTask;
import quilt.internal.util.EnigmaProfileService;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static org.quiltmc.enigma_plugin.Arguments.SIMPLE_TYPE_FIELD_NAMES_PATH;

/**
 * <ul>
 *     <li> creates the {@value QuiltMappingsExtension#EXTENSION_NAME} extension
 *     <li> {@linkplain org.gradle.api.tasks.TaskContainer#register registers} the
 *          {@value EnigmaProfileService#ENIGMA_PROFILE_SERVICE_NAME} service
 *     <li> {@linkplain org.gradle.api.tasks.TaskCollection#configureEach configures}
 *          {@link EnigmaProfileConsumingTask}s
 *     <li> {@linkplain org.gradle.api.tasks.TaskCollection#configureEach configures}
 *          {@link MappingsDirOutputtingTask}s
 *     <li> {@linkplain org.gradle.api.tasks.TaskCollection#configureEach configures}
 *          {@link MappingsDirConsumingTask}s
 * </ul>
 */
public abstract class QuiltMappingsBasePlugin implements MappingsProjectPlugin {
    static final String MAPPINGS_NAME_PREFIX = Constants.MAPPINGS_NAME + "-";
    static final String ARCHIVE_FILE_NAME_PREFIX = MAPPINGS_NAME_PREFIX + Constants.MAPPINGS_VERSION;

    @Nullable
    private QuiltMappingsExtension ext;

    @Override
    public void apply(@NotNull Project project) {
        this.ext = project.getExtensions()
            .create(QuiltMappingsExtension.EXTENSION_NAME, QuiltMappingsExtension.class);

        final BuildServiceRegistry services = project.getGradle().getSharedServices();

        final var enigmaProfile = services.registerIfAbsent(
            EnigmaProfileService.ENIGMA_PROFILE_SERVICE_NAME,
            EnigmaProfileService.class,
            spec -> spec.parameters(params -> {
                params.getProfileConfig().convention(this.ext.getEnigmaProfileConfig());
            })
        );

        final TaskContainer tasks = project.getTasks();

        // save this in a property so all tasks use the same cached value
        final var simpleTypeFieldNamePaths = this.getObjects().listProperty(String.class);
        simpleTypeFieldNamePaths.set(
            enigmaProfile
                .map(EnigmaProfileService::getProfile)
                .map(profile ->
                    profile.getServiceProfiles(JarIndexerService.TYPE).stream()
                        .flatMap(service -> service.getArgument(SIMPLE_TYPE_FIELD_NAMES_PATH).stream())
                        .map(stringOrStrings -> stringOrStrings.mapBoth(Stream::of, Collection::stream))
                        .flatMap(bothStringStreams ->
                            bothStringStreams.left().orElseGet(bothStringStreams::rightOrThrow)
                        )
                        .toList()
                )
        );

        tasks.withType(EnigmaProfileConsumingTask.class).configureEach(task -> {
            task.getEnigmaProfileService().convention(enigmaProfile);

            task.getEnigmaProfileConfig().convention(this.ext.getEnigmaProfileConfig());

            task.getSimpleTypeFieldNamesFiles().from(simpleTypeFieldNamePaths);
        });

        this.provideDefaultError(
            this.ext.getEnigmaProfileConfig(),
            "No enigma profile specified. " +
                "A profile must be specified to use an " + EnigmaProfileConsumingTask.class.getSimpleName() + "."
        );

        final var mappingsDirOutputtingTasks = tasks.withType(MappingsDirOutputtingTask.class);

        mappingsDirOutputtingTasks.configureEach(task -> {
            task.getMappingsDir().convention(this.ext.getMappingsDir());
        });

        tasks.withType(MappingsDirConsumingTask.class).configureEach(task -> {
            task.getMappingsDir().convention(this.ext.getMappingsDir());
            task.getInputs().files(mappingsDirOutputtingTasks);
        });

        this.provideDefaultError(
            this.ext.getMappingsDir(),
            "No mappings directory specified. " +
                "A directory must be specified to use a " + MappingsDirConsumingTask.class.getSimpleName() + "."
        );
    }

    public QuiltMappingsExtension getExt() {
        return Objects.requireNonNull(this.ext, "Extension not yet registered");
    }
}
