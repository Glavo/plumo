plugins {
    id("application")
}

val mainClassName = "org.glavo.webdav.WebDAV"

application {
    mainClass.set(mainClassName)
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
}

val versionFile = layout.buildDirectory.file("version.txt")

val generateVersionFile by tasks.registering {
    outputs.file(versionFile)

    doLast {
        versionFile.get().asFile.writeText(project.version.toString())
    }
}

tasks.processResources {
    dependsOn(tasks["generateVersionFile"])

    into("org/glavo/webdav") {
        from(versionFile)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":plumo"))
}
