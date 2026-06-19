import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("local.java-conventions")
}

val localMavenRepositories = configurations.dependencyScope("local-maven-repositories")
val resolvableLocalMavenRepositories =
    configurations.resolvable("resolvable-local-maven-repositories") {
        extendsFrom(localMavenRepositories)
        // Same attributes  as in local.maven-publish convention plugin
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("maven-repository"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }

dependencies {
    localMavenRepositories(projects.annotations)
    localMavenRepositories(projects.processor)
}

val prepareLocalRepo =
    tasks.register<Sync>("prepareLocalRepo") {
        from(resolvableLocalMavenRepositories)
        into(layout.buildDirectory.dir("local-maven-repo"))
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
                        val metadata =
                            this.project.javaToolchains
                                .launcherFor {
                                    this.languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                                }.get()
                                .metadata
                        systemProperty("test.java-home", metadata.installationPath.asFile.canonicalPath)
                    }

                    val testGradleVersion = project.findProperty("test.gradle-version")
                    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

                    systemProperty("version", rootProject.version.toString())

                    dependsOn(prepareLocalRepo) // XXX: shouldn't be necessary?
                    jvmArgumentProviders.add(
                        objects.newInstance<TestRepository>().apply {
                            testRepository
                                .from(prepareLocalRepo.map { it.destinationDir })
                                .exclude("**/maven-metadata.*")
                        },
                    )
                }
            }
        }
    }
}

abstract class TestRepository
    @Inject
    constructor() :
    CommandLineArgumentProvider,
        Named {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val testRepository: ConfigurableFileTree

        @Internal
        override fun getName() = "testRepository"

        override fun asArguments() = listOf("-DtestRepository=${testRepository.dir.absoluteFile.toURI().toASCIIString()}")
    }
