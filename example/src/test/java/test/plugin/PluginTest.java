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
package test.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PluginTest {
  public static final GradleVersion testGradleVersion =
      Optional.ofNullable(System.getProperty("test.gradle-version"))
          .map(GradleVersion::version)
          .orElseGet(GradleVersion::current);

  public static final String testJavaHome =
      System.getProperty("test.java-home", System.getProperty("java.home"));

  @TempDir Path projectDir;

  @Test
  void test() throws Exception {
    try (var w = Files.newBufferedWriter(projectDir.resolve("gradle.properties"))) {
      var properties = new Properties();
      properties.setProperty("org.gradle.java.home", testJavaHome);
      properties.store(w, null);
    }
    Files.createFile(projectDir.resolve("settings.gradle.kts"));
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        /* language=kotlin */
        """
import test.plugin.errorprone
import test.plugin.nullaway
import test.plugin.reproducibility
import test.plugin.testPlugin

plugins {
    `java`
    `application`
    id("test.plugin")
}

tasks {
    compileJava {
        options.errorprone.configureErrorProne()
        options.errorprone.nullaway.configureNullAway()

        options.errorprone {
            configureErrorProne()
            nullaway.configureNullAway()
            nullaway {
                configureNullAway()
            }
        }
    }

    jar {
        reproducibility.configureReproducibility()
        reproducibility {
            configureReproducibility()
        }
    }

    distZip {
        reproducibility.configureReproducibility()
        reproducibility {
            configureReproducibility()
        }
    }

    distTar {
        reproducibility.configureReproducibility()
        reproducibility {
            configureReproducibility()
        }
    }
}

distributions {
    main {
        testPlugin.configureDistribution()
        testPlugin {
            configureDistribution()
        }
    }
    create("test") {
        testPlugin.configureDistribution()
        testPlugin {
            configureDistribution()
        }
    }
}
""");

    GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("build", "--stacktrace")
        .forwardOutput()
        .build();
  }
}
