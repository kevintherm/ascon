import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import io.github.takahirom.roborazzi.RoborazziExtension

/**
 * A feature module: an Android library with Compose, the core module, ViewModels,
 * and Robolectric screenshot tests recorded with Roborazzi.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("ascon.android.library")
            pluginManager.apply("ascon.android.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<LibraryExtension> {
                testOptions.unitTests.isIncludeAndroidResources = true
                // Robolectric reaches into FileDescriptor internals, which new JDKs close by default.
                testOptions.unitTests.all {
                    it.jvmArgs(
                        "--add-opens=java.base/java.io=ALL-UNNAMED",
                        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    )
                }
            }
            // Golden images live next to the tests so reviews show what changed.
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            fun lib(alias: String) = libs.findLibrary(alias).get()
            dependencies {
                add("implementation", project(":core"))
                add("implementation", lib("androidx-lifecycle-runtime-compose"))
                add("implementation", lib("androidx-lifecycle-viewmodel-compose"))

                add("testImplementation", lib("junit"))
                add("testImplementation", lib("kotlinx-coroutines-test"))
                add("testImplementation", lib("androidx-test-ext-junit"))
                add("testImplementation", lib("robolectric"))
                add("testImplementation", lib("roborazzi"))
                add("testImplementation", lib("roborazzi-compose"))
                add("testImplementation", lib("roborazzi-junit-rule"))
                add("testImplementation", lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
