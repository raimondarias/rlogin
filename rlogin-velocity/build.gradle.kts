dependencies {
    implementation(project(":rlogin-common"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}
