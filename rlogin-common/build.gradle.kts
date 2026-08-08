dependencies {
    api(project(":rlogin-api"))

    implementation(libs.hikaricp)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mysql.connector)
    implementation(libs.snakeyaml)
    implementation(libs.bcrypt)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
