/*
 * Copyright © 2026 Thomas Broyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ltgt.gradle.kotlin.accessors.generator.integTest;

import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GenerateKotlinAccessorsIntegrationTest {
  public static final GradleVersion testGradleVersion =
      Optional.ofNullable(System.getProperty("test.gradle-version"))
          .map(GradleVersion::version)
          .orElseGet(GradleVersion::current);

  public static final String testJavaHome =
      System.getProperty("test.java-home", System.getProperty("java.home"));

  public static final String version = requireNonNull(System.getProperty("version"));

  public static final String testRepository = requireNonNull(System.getProperty("testRepository"));

  @TempDir Path projectDir;

  @Test
  void test() throws Exception {
    try (var w = Files.newBufferedWriter(projectDir.resolve("gradle.properties"))) {
      var properties = new Properties();
      properties.setProperty("org.gradle.java.home", testJavaHome);
      properties.store(w, null);
    }
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        /* language=kotlin */
        """
pluginManagement {
    includeBuild("build-logic")
}
""");
    Files.createDirectories(projectDir.resolve("build-logic"));
    Files.createFile(projectDir.resolve("build-logic/settings.gradle.kts"));
    Files.writeString(
        projectDir.resolve("build-logic/build.gradle.kts"),
            /* language=kotlin */
            """
plugins {
    `java-gradle-plugin`
}
gradlePlugin {
    plugins {
        register("local.test") {
            id = "local.test"
            implementationClass = "test.plugin.TestPlugin"
        }
    }
}
repositories {
    exclusiveContent {
        forRepository {
            maven("%1$s")
        }
        filter {
            includeGroup("net.ltgt.gradle.kotlin-accessors-generator")
        }
    }
    mavenCentral()
}
dependencies {
    compileOnly("net.ltgt.gradle.kotlin-accessors-generator:annotations:%2$s")
    annotationProcessor("net.ltgt.gradle.kotlin-accessors-generator:processor:%2$s")
}
tasks {
    compileJava {
        options.compilerArgs.add("-Anet.ltgt.gradle.kotlin.accessors.generator.kotlinModuleName=testPlugin")
    }
}
"""
            .formatted(testRepository, version));
    Files.createDirectories(projectDir.resolve("build-logic/src/main/java/test/plugin"));
    Files.writeString(
        projectDir.resolve("build-logic/src/main/java/test/plugin/TestPlugin.java"),
        /* language=java */
        """
package test.plugin;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

@GenerateKotlinAccessors(
    className = "TestPluginKt",
    extensions = {
      @GenerateKotlinAccessors.Extension(
          name = SimpleExtension.NAME,
          extended = Test.class,
          extension = SimpleExtension.class),
    }
)
public class TestPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getTasks().withType(Test.class).configureEach(
        test -> test.getExtensions().create(SimpleExtension.NAME, SimpleExtension.class));
  }
}
""");
    Files.writeString(
        projectDir.resolve("build-logic/src/main/java/test/plugin/SimpleExtension.java"),
        /* language=java */
        """
package test.plugin;

public class SimpleExtension {
  public static final String NAME = "simple";

  public void configureSimple() {}
}
""");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        /* language=kotlin */
        """
import test.plugin.simple

plugins {
    `java-library`
    id("local.test")
}

tasks {
    test {
        simple.configureSimple()
    }
}
""");

    GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(projectDir.toFile())
        .withArguments("tasks", "--stacktrace")
        .forwardOutput()
        .build();
  }
}
