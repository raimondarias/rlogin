dependencies {
    implementation(project(":rlogin-common"))
    implementation(libs.bstats.velocity)
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}
