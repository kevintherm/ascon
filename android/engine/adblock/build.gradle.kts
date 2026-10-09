import org.gradle.internal.os.OperatingSystem

plugins {
    id("ascon.android.library")
}

android {
    namespace = "com.ascon.engine.adblock"
    // cargo-ndk links the Rust library with this NDK.
    ndkVersion = "28.2.13676358"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// The engine is Brave's adblock-rust, built from rust/ with cargo-ndk and bound to Kotlin
// with UniFFI. Install rustup, the Android targets and cargo-ndk first; see the README.

/** A cargo command whose results land in [outputDir]. */
abstract class CargoTask : Exec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val rustDir = layout.projectDirectory.dir("rust")
val cargoHome = File(System.getProperty("user.home"), ".cargo/bin")
val cargo = File(cargoHome, "cargo").takeIf { it.exists() }?.path ?: "cargo"
val rustSources =
    files(rustDir.dir("src"), rustDir.file("Cargo.toml"), rustDir.file("Cargo.lock"), rustDir.file("uniffi.toml"))

val buildRust = tasks.register<CargoTask>("buildRust") {
    description = "Builds the Rust engine for each Android ABI."
    inputs.files(rustSources)
    val out = layout.buildDirectory.dir("rust/jniLibs")
    outputDir.set(out)
    workingDir(rustDir)
    environment("PATH", "${cargoHome.path}:${System.getenv("PATH")}")
    environment("ANDROID_NDK_HOME", androidComponents.sdkComponents.ndkDirectory.get().asFile.path)
    commandLine(
        cargo, "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64", "-P", "26",
        "-o", out.get().asFile.path, "build", "--release"
    )
}

val generateBindings = tasks.register<CargoTask>("generateBindings") {
    description = "Generates the Kotlin bindings from a build of the Rust engine for this machine."
    inputs.files(rustSources)
    val out = layout.buildDirectory.dir("rust/kotlin").get().asFile.path
    outputDir.set(layout.buildDirectory.dir("rust/kotlin"))
    workingDir(rustDir)
    environment("PATH", "${cargoHome.path}:${System.getenv("PATH")}")
    // Release Android builds are stripped of the metadata UniFFI reads, so read a host build.
    val hostLibrary = if (OperatingSystem.current().isMacOsX) "libascon_adblock.dylib" else "libascon_adblock.so"
    commandLine(
        "sh",
        "-c",
        "\"$cargo\" build --lib -q && \"$cargo\" run -q --bin uniffi-bindgen generate " +
            "--library target/debug/$hostLibrary --language kotlin --no-format --out-dir \"$out\""
    )
}

androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRust, CargoTask::outputDir)
    variant.sources.java?.addGeneratedSourceDirectory(generateBindings, CargoTask::outputDir)
}

dependencies {
    implementation(project(":core"))
    // UniFFI's Kotlin bindings call the Rust library through JNA.
    implementation(libs.jna) { artifact { type = "aar" } }

    testImplementation(libs.junit)
}
