plugins {
    id("java-library")
}

subprojects {
    group = "org.glavo"
    version = "0.1.0" + "-SNAPSHOT"

    if (!project.name.startsWith("plumo")) {
        return@subprojects
    }

    apply {
        plugin("java-library")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnlyApi("org.jetbrains:annotations:24.1.0")
        testImplementation(platform("org.junit:junit-bom:5.10.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")

        // https://mvnrepository.com/artifact/org.apache.httpcomponents/httpclient
        testImplementation("org.apache.httpcomponents:httpclient:4.5.14")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
