plugins {
    id("application")
}

val mainClassName = "org.glavo.plumo.webserver.WebServer"

application {
    mainClass.set(mainClassName)
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
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

    into("org/glavo/plumo/webserver") {
        from(versionFile)
    }
}

dependencies {
    implementation(project(":plumo-core"))
}
