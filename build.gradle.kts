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
        testImplementation(platform("org.junit:junit-bom:5.11.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")

        // https://mvnrepository.com/artifact/org.apache.httpcomponents/httpclient
        testImplementation("org.apache.httpcomponents:httpclient:4.5.14")
        // https://mvnrepository.com/artifact/com.google.code.gson/gson
        testImplementation("com.google.code.gson:gson:2.10.1")
        // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
        testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
