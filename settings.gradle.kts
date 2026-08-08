rootProject.name = "rlogin"

include(
    "rlogin-api",
    "rlogin-common",
    "rlogin-velocity",
    "rlogin-paper"
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
