plugins {
    `java-library`
    id("local.java-conventions")
    id("local.maven-publish")
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    implementation(projects.annotations)
    implementation(libs.kotlin.metadata.jvm)

    compileOnly(libs.incapHelper.annotations)
    annotationProcessor(libs.incapHelper.processor)

    compileOnly(libs.autoService.annotations)
    annotationProcessor(libs.autoService.processor)

    compileOnly(libs.autoValue.annotations)
    annotationProcessor(libs.autoValue.processor)
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter(libs.versions.junitJupiter)
        }
        val test by existing(JvmTestSuite::class) {
            dependencies {
                implementation(libs.compileTesting)
                implementation(libs.truth)
                runtimeOnly(gradleApi())

                // We could use compileOnlyApi above, but we don't want the dependency in the POM.
                // This is OK because annotation processors aren't regular Java libraries you compile against.
                compileOnly(libs.incapHelper.annotations)
                compileOnly(libs.autoService.annotations)
                compileOnly(libs.autoValue.annotations)
            }
            targets.configureEach {
                testTask {
                    jvmArgs(
                        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                    )
                }
            }
        }
    }
}
tasks {
    check {
        dependsOn(testing.suites)
    }
}
