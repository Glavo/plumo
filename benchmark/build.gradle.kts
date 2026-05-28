plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

val jmhVersion = "1.37"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

    implementation(project(":plumo"))
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.openjdk.jmh.Main"
        )
    }
}
