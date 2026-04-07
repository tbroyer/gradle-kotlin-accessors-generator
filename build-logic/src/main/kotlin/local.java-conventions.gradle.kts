import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("local.common-conventions")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
    id("org.gradlex.jvm-dependency-conflict-resolution")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

// Configure toolchain only if needed
if (!JavaVersion.current().isCompatibleWith(java.sourceCompatibility)) {
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(java.sourceCompatibility.majorVersion)
        }
    }
}

jvmDependencyConflicts {
    patch {
        module("com.google.truth:truth") {
            // See https://github.com/google/truth/issues/333
            reduceToRuntimeOnlyDependency("junit:junit")
        }
        module("com.google.testing.compile:compile-testing") {
            // junit is actually a "peer" dependency; only needed if you already use it
            removeDependency("junit:junit")
        }
    }
}

dependencies {
    errorprone(
        versionCatalogs
            .named("libs")
            .findBundle("errorprone")
            .orElseThrow(),
    )
}

nullaway {
    annotatedPackages.addAll("net.ltgt.gradle.kotlin.accessors.generator", "test.plugin")
    jspecifyMode = true
}

tasks.withType<JavaCompile>().configureEach {
    options.release = java.sourceCompatibility.majorVersion.toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all,-fallthrough,-processing"))
    options.errorprone {
        enable("DefaultLocale")
        error("WildcardImport")
        // XXX: text blocks aren't supported in --release 8
        // https://github.com/google/error-prone/issues/4931
        disable("StringConcatToTextBlock")
    }
}

tasks {
    compileJava {
        options.release = 8
        // release=8 is deprecated starting with JDK 21
        options.compilerArgs.add("-Xlint:all,-options")
    }
}

testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter(
                versionCatalogs
                    .named("libs")
                    .findVersion("junitJupiter")
                    .orElseThrow()
                    .requiredVersion,
            )
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        noTimestamp()
        quiet()
        use()
        addStringOption("-release", java.sourceCompatibility.majorVersion)
        addBooleanOption("Xdoclint:-missing", true)
    }
}

spotless {
    java {
        removeUnusedImports()
        forbidWildcardImports()
        forbidModuleImports()
        googleJavaFormat(
            versionCatalogs
                .named("libs")
                .findVersion("googleJavaFormat")
                .orElseThrow()
                .requiredVersion,
        ).reorderImports(true)
        licenseHeaderFile(rootProject.file("LICENSE.header"))
    }
}
