tasks.compileJava {
    options.release.set(9)
}

dependencies {
    implementation(project(":plumo"))
}