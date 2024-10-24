package quilt.internal.tasks.setup;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.specs.Spec;
import quilt.internal.plugin.MapIntermediaryPlugin;
import quilt.internal.plugin.QuiltMappingsPlugin;
import quilt.internal.tasks.MappingsTask;

/**
 * A task that depends on {@value quilt.internal.Constants#INTERMEDIARY_MAPPINGS_NAME} mappings
 * for the current Minecraft version.
 * <p>
 * If {@link QuiltMappingsPlugin QuiltMappingsPlugin} is applied,
 * {@code IntermediaryDependantTask}s will {@linkplain org.gradle.api.Task#onlyIf(Spec) only run} if
 * {@value MapIntermediaryPlugin#INTERMEDIARY_MAPPINGS_CONFIGURATION_NAME}
 * {@link org.gradle.api.artifacts.Configuration Configuration} successfully
 * {@linkplain Configuration#resolve() resolves}.
 */
public interface IntermediaryDependantTask extends MappingsTask { }
