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

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

@GenerateKotlinAccessors(
    className = "TestPluginKt",
    extensions = {
      @GenerateKotlinAccessors.Extension(
          name = ErrorProneOptions.NAME,
          extended = CompileOptions.class,
          extension = ErrorProneOptions.class),
      @GenerateKotlinAccessors.Extension(
          name = NullAwayExtension.NAME,
          extended = ErrorProneOptions.class,
          extension = NullAwayExtension.class),
      @GenerateKotlinAccessors.Extension(
          name = ReproducibilityExtension.NAME,
          extension = ReproducibilityExtension.class,
          extended = {Zip.class, Tar.class}),
      @GenerateKotlinAccessors.Extension(
          name = DistributionExtension.NAME,
          extension = DistributionExtension.class,
          extended = Distribution.class),
      @GenerateKotlinAccessors.Extension(
          name = SimpleExtension.NAME,
          extension = SimpleExtension.class,
          extended = TestPlugin.TestExtension.class),
    })
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

    TestExtension ext = project.getExtensions().create("ltgt", TestExtension.class);
    ext.getExtensions().create(SimpleExtension.NAME, SimpleExtension.class);
  }

  // Let's put a dollar in there
  public interface TestExtension extends ExtensionAware {}
}
