plugins {
    id("application")
}

val mainClassName = "org.glavo.plumo.Plumo"

application {
    mainClass.set(mainClassName)
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
}
