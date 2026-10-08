import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies the Android library plugin with the project's SDK and JVM levels. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = AndroidConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = AndroidConfig.MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = AndroidConfig.JAVA_VERSION
                    targetCompatibility = AndroidConfig.JAVA_VERSION
                }
            }
        }
    }
}
