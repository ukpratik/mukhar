import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).get().requiredVersion

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Root package for every module: `:core:data` → `io.github.ukpratik.mukhar.data` (LLD §1). */
internal fun Project.mukharNamespace(): String = "io.github.ukpratik.mukhar." + path.substringAfterLast(':')

/** LLD §8: Android Lint is the static-analysis gate. Version nags are off (upgrades are deliberate). */
internal fun Lint.mukharDefaults() {
    warningsAsErrors = true
    abortOnError = true
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
}
