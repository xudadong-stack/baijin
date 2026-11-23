plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

val testSourceSet = sourceSets.test.get()
tasks.processTestResources {
    from(rootProject.layout.projectDirectory.file("app/src/main/assets/content/sample.json")) {
        into("content")
    }
}

tasks.test {
    useJUnit()
}

tasks.register<Test>("validateContent") {
    group = "verification"
    description = "Validates the sample JSON packaged by app."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnit()
    filter.includeTestsMatching("com.baijin.game.core.content.BundledContentTest")
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
