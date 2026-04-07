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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

public class TestPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            javaCompile -> {
              ErrorProneOptions options =
                  ((ExtensionAware) javaCompile.getOptions())
                      .getExtensions()
                      .create(ErrorProneOptions.NAME, ErrorProneOptions.class);
              ((ExtensionAware) options)
                  .getExtensions()
                  .create(NullAwayExtension.NAME, NullAwayExtension.class);
            });

    project
        .getTasks()
        .withType(Zip.class)
        .configureEach(
            zip -> {
              zip.getExtensions()
                  .create(ReproducibilityExtension.NAME, ReproducibilityExtension.class);
            });
    project
        .getTasks()
        .withType(Tar.class)
        .configureEach(
            tar -> {
              tar.getExtensions()
                  .create(ReproducibilityExtension.NAME, ReproducibilityExtension.class);
            });

    project
        .getPluginManager()
        .withPlugin(
            "distribution",
            ignored -> {
              project
                  .getExtensions()
                  .getByType(DistributionContainer.class)
                  .configureEach(
                      distribution -> {
                        ((ExtensionAware) distribution)
                            .getExtensions()
                            .create(
                                DistributionExtension.class,
                                DistributionExtension.NAME,
                                DefaultDistributionExtension.class);
                      });
            });
  }
}
