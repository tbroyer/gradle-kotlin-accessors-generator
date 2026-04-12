plugins {
    `java-library`
    id("local.java-conventions")
}

dependencies {
    compileOnlyApi(libs.jetbrains.annotations)

    implementation(projects.annotations)
    implementation(libs.kotlin.metadata.jvm)

    compileOnlyApi(libs.incapHelper.annotations)
    annotationProcessor(libs.incapHelper.processor)

    compileOnlyApi(libs.autoService.annotations)
    annotationProcessor(libs.autoService.processor)

    compileOnlyApi(libs.autoValue.annotations)
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
