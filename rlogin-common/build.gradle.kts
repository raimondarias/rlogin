dependencies {
    api(project(":rlogin-api"))

    implementation(libs.hikaricp)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mysql.connector)
    implementation(libs.snakeyaml)
    implementation(libs.bcrypt)
    implementation(libs.gson)

    // Provided at runtime by Paper/Velocity (both bundle Adventure end-to-end
    // and expose it to plugin classloaders) — never shaded, see rlogin-plugin.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.legacy)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.legacy)
    testRuntimeOnly(libs.junit.platform.launcher)
}
