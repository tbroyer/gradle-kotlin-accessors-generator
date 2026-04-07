import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("local.java-conventions")
}

tasks {
    compileJava {
        options.release = 8
        // release=8 is deprecated starting with JDK 21
        options.compilerArgs.add("-Xlint:all,-options")
    }
}
