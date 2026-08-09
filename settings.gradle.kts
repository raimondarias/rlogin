rootProject.name = "rlogin"

include(
    "rlogin-api",
    "rlogin-common",
    "rlogin-velocity",
    "rlogin-paper",
    "rlogin-plugin"
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/") // PacketEvents
    }
}
