dependencies {
    implementation(project(":rlogin-common"))
    compileOnly(libs.paper.api)
    // Soft dependency: only used by the optional standalone hybrid-auth mode, and only
    // if the separate PacketEvents plugin is actually installed — see PacketEventsSupport.
    compileOnly(libs.packetevents.spigot)
    // Provided at runtime: it's the logging backend the server itself runs on. Only
    // touched by CommandLogFilter, which degrades to a warning if it isn't Log4j2.
    compileOnly(libs.log4j.core)
    // Shaded (see rlogin-plugin): bStats mandates a relocated copy per plugin.
    implementation(libs.bstats.bukkit)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
