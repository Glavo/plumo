plugins {
    id("java-library")
}

subprojects {
    apply {
        plugin("java-library")
    }

    group = "org.glavo"
    version = "0.1.0" + "-SNAPSHOT"

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
