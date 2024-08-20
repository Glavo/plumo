/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
