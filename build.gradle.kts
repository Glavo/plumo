plugins {
    id("java-library")
    id("org.glavo.compile-module-info-plugin") version "2.0"
}

subprojects {
    group = "org.glavo"
    version = "0.1.0" + "-SNAPSHOT"

    if (!project.name.startsWith("plumo-")) {
        return@subprojects
    }

    apply {
        plugin("java-library")
        plugin("org.glavo.compile-module-info-plugin")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnlyApi("org.jetbrains:annotations:24.0.1")
        testImplementation(platform("org.junit:junit-bom:5.9.3"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.compileJava {
        sourceCompatibility = "9"
        options.release.set(8)
    }

    tasks.test {
        useJUnitPlatform()
    }
}
