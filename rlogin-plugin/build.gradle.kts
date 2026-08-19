// rlogin-plugin: the actual distributable artifact. Bundles rlogin-paper's
// and rlogin-velocity's compiled classes (plus rlogin-common and its shaded
// libs) into ONE jar: rLogin-<version>.jar.
//
// This works because Paper and Velocity discover their entry point in
// completely different, non-conflicting ways:
//   - Paper reads /plugin.yml at the jar root and reflectively loads the
//     class named in its `main:` key.
//   - Velocity's annotation processor emits /velocity-plugin.json at the
//     jar root (from the @Plugin-annotated class) at compile time; the
//     proxy reads that descriptor directly, it doesn't classload anything
//     it isn't told to load.
// Neither platform loads classes that need the other platform's API on the
// classpath, so paper-api/velocity-api being compileOnly-only (never
// shaded) is exactly what makes this safe: drop the same jar in a Paper
// server's plugins/ and in Velocity's plugins/, and each one only ever
// touches its own half.
plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":rlogin-paper"))
    implementation(project(":rlogin-velocity"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("rLogin")

    relocate("com.zaxxer.hikari", "com.raimondarias.rlogin.libs.hikari")
    // org.sqlite is NOT relocated on purpose: sqlite-jdbc's native (JNI) library
    // is prebuilt with symbol names baked in for the org.sqlite.* package. Renaming
    // the Java package breaks that binding (UnsatisfiedLinkError on NativeDB.open),
    // since the .so/.dll can't be renamed to match. Leaving it unrelocated is the
    // standard, documented workaround for shading sqlite-jdbc.
    relocate("com.mysql", "com.raimondarias.rlogin.libs.mysql")
    relocate("org.yaml.snakeyaml", "com.raimondarias.rlogin.libs.snakeyaml")
    relocate("at.favre.lib.crypto.bcrypt", "com.raimondarias.rlogin.libs.bcrypt")
    relocate("com.google.gson", "com.raimondarias.rlogin.libs.gson")
    // Required by bStats itself: two plugins shipping the same unrelocated
    // org.bstats classes would clash over which one initialises first.
    relocate("org.bstats", "com.raimondarias.rlogin.libs.bstats")

    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// This module has no sources of its own, nothing to compile/test/javadoc.
tasks.named("jar") {
    enabled = false
}
