plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val jmhVersion = "1.37"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

    implementation(project(":plumo-core"))
}

tasks.compileJava {
    options.release.set(17)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.openjdk.jmh.Main"
        )
    }
}
