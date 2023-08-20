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

dependencies {
    implementation(project(":plumo-core"))
}
