plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":rlogin-common"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("rLogin-Velocity")

    // Reubicamos las libs embebidas para no chocar con otros plugins del proxy.
    relocate("com.zaxxer.hikari", "com.raimondarias.rlogin.libs.hikari")
    relocate("org.sqlite", "com.raimondarias.rlogin.libs.sqlite")
    relocate("com.mysql", "com.raimondarias.rlogin.libs.mysql")
    relocate("org.yaml.snakeyaml", "com.raimondarias.rlogin.libs.snakeyaml")
    relocate("at.favre.lib.crypto.bcrypt", "com.raimondarias.rlogin.libs.bcrypt")

    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
