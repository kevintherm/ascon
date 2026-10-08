import org.gradle.api.JavaVersion

/** SDK and JVM levels shared by every Android module. */
object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 37
    val JAVA_VERSION = JavaVersion.VERSION_17
}
