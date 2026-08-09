plugins {
    java
}

allprojects {
    group = "com.raimondarias.rlogin"
    version = "1.1.2"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/") // PacketEvents
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    java {
        toolchain {
            // Compile against the minimum supported version (Java 21). The resulting
            // bytecode runs unchanged on any later JRE (22, 23, 24, 25, 26...), so
            // there's no need to recompile when the server's Java version goes up.
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
    }

    tasks.withType<ProcessResources> {
        filteringCharset = "UTF-8"
    }
}

// Published so third-party plugins can compile against rLogin instead of
// copying its class names and hoping. JitPack builds these from a git tag,
// which means no credentials to hold and no artifact that can drift from the
// source it claims to come from.
configure(listOf(project(":rlogin-api"), project(":rlogin-paper"))) {
    apply(plugin = "maven-publish")
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = project.name
            }
        }
    }
}
