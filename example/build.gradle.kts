import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    id("local.java-conventions")
}

tasks {
    compileJava {
        options.release = 11

        options.compilerArgs.add("-Anet.ltgt.gradle.kotlin.accessors.generator.kotlinModuleName=testPlugin")

        shouldRunAfter("${project.projects.processor.path}:test")
    }
}

dependencies {
    compileOnly(projects.annotations)
    annotationProcessor(projects.processor)

    compileOnly(libs.jetbrains.annotations)
}

gradlePlugin {
    plugins {
        register("test.plugin") {
            implementationClass = "test.plugin.TestPlugin"
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            dependencies {
                implementation(gradleTestKit())
            }

            targets.configureEach {
                testTask {
                    testLogging {
                        showExceptions = true
                        showStackTraces = true
                        exceptionFormat = TestExceptionFormat.FULL
                    }
                    val testJavaToolchain = project.findProperty("test.java-toolchain")
                    testJavaToolchain?.also {
                        val launcher =
                            project.javaToolchains.launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                            }
                        val metadata = launcher.get().metadata
                        systemProperty("test.java-home", metadata.installationPath.asFile.canonicalPath)
                    }

                    val testGradleVersion = project.findProperty("test.gradle-version")
                    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }
                }
            }
        }
    }
}
