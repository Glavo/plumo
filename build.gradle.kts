plugins {
    id("java-library")
    id("application")
}

group = "org.glavo"
version = "0.1.0" + "-SNAPSHOT"

val mainClassName = "org.glavo.webdav.WebDAVServer"

application {
    mainClass.set(mainClassName)
}

repositories {
    mavenCentral()
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
}

dependencies {
    compileOnlyApi("org.jetbrains:annotations:24.0.1")
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}