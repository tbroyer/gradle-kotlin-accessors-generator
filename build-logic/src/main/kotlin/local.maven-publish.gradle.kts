plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
    signing
}

group = "net.ltgt.gradle.kotlin-accessors-generator"

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = provider { "${project.group}:${project.name}" }
        description = provider { project.description }.orElse(name)
        url = "https://github.com/tbroyer/gradle-kotlin-accessors-generator"
        developers {
            developer {
                name = "Thomas Broyer"
                email = "t.broyer@ltgt.net"
            }
        }
        scm {
            connection = "https://github.com/tbroyer/gradle-kotlin-accessors-generator.git"
            developerConnection = "scm:git:ssh://github.com:tbroyer/gradle-kotlin-accessors-generator.git"
            url = "https://github.com/tbroyer/gradle-kotlin-accessors-generator"
        }
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            versionMapping {
                usage(Usage.JAVA_API) {
                    fromResolutionOf(configurations.runtimeClasspath.name)
                }
                usage(Usage.JAVA_RUNTIME) {
                    fromResolutionResult()
                }
            }
        }
    }
}

signing {
    useGpgCmd()
}
