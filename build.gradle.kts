plugins {
    id("java")
}

group = "org.glavo"
version = "0.1.0" + "-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}