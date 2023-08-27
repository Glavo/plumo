plugins {
    id("application")
    id("org.glavo.compile-module-info-plugin") version "2.0"
}

val mainClassName = "org.glavo.plumo.fileserver.FileServer"

application {
    mainClass.set(mainClassName)
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
}

tasks.compileJava {
    sourceCompatibility = "9"
    options.release.set(8)
}

val versionFile = layout.buildDirectory.file("version.txt")

tasks.create("generateVersionFile") {
    outputs.file(versionFile)

    doLast {
        versionFile.get().asFile.writeText(project.version.toString())
    }
}

tasks.processResources {
    dependsOn(tasks["generateVersionFile"])

    into("org/glavo/plumo/fileserver") {
        from(versionFile)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":plumo-core"))
}
