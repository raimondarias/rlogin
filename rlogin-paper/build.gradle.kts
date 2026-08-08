plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":rlogin-common"))
    compileOnly(libs.paper.api)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("rLogin-Paper")

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
