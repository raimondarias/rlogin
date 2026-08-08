plugins {
    java
}

allprojects {
    group = "com.raimondarias.rlogin"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    java {
        toolchain {
            // Compilar en el mínimo soportado (Java 21). El bytecode resultante
            // corre sin cambios en cualquier JRE posterior (22, 23, 24, 25, 26...),
            // así que no hace falta recompilar al subir de versión de Java.
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
