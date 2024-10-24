package quilt.internal;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.quiltmc.enigma.api.EnigmaProfile;

import javax.inject.Inject;

public abstract class QuiltMappingsExtension {
    public static final String EXTENSION_NAME = "quiltMappings";

    @Inject
    protected QuiltMappingsExtension(Project project) {
        this.unpickVersion = project.getExtensions().getByType(VersionCatalogsExtension.class)
            .named("libs")
            .findVersion(Constants.UNPICK_NAME)
            .map(VersionConstraint::getRequiredVersion)
            .orElseThrow(() -> new GradleException(
                """
                Could not find unpick version.
                \tAn 'unpick' version must be specified in the 'libs' version catalog,
                \tusually by adding it to 'gradle/libs.versions.toml'.
                """
            ));
    }

    public abstract DirectoryProperty getMappingsDir();

    /**
     * Don't parse this to create an {@link EnigmaProfile}, use the
     * {@value quilt.internal.util.EnigmaProfileService#ENIGMA_PROFILE_SERVICE_NAME} service's
     * {@link quilt.internal.util.EnigmaProfileService#getProfile() profile} instead.
     * <p>
     * This is exposed so it can be passed to external processes.
     */
    public abstract RegularFileProperty getEnigmaProfileConfig();

    public abstract RegularFileProperty getUnpickMeta();

    private final String unpickVersion;

    public String getUnpickVersion() {
        return this.unpickVersion;
    }

}
