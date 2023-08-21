plugins {
    id("application")
    id("org.glavo.compile-module-info-plugin") version "2.0"
}

val mainClassName = "org.glavo.plumo.Plumo"

application {
    mainClass.set(mainClassName)
    applicationDefaultJvmArgs = listOf("--enable-preview")
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
