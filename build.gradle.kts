plugins {
    id("java-library")
}

subprojects {
    group = "org.glavo"
    version = "0.1.0" + "-SNAPSHOT"

    if (!project.name.startsWith("plumo-")) {
        return@subprojects
    }

    apply {
        plugin("java-library")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnlyApi("org.jetbrains:annotations:24.0.1")
        testImplementation(platform("org.junit:junit-bom:5.10.1"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
