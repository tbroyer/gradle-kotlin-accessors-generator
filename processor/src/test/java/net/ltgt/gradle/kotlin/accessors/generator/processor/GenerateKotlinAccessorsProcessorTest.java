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
package net.ltgt.gradle.kotlin.accessors.generator.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaFileObjectSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;

import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class GenerateKotlinAccessorsProcessorTest {
  private Compiler getCompiler() {
    return Compiler.javac()
        .withProcessors(new GenerateKotlinAccessorsProcessor())
        .withOptions(
            "--release=8",
            "-Xlint:-options", // release=8 is deprecated starting with JDK 21
            "-A%s=foo".formatted(GenerateKotlinAccessorsProcessor.KOTLIN_MODULE_NAME));
  }

  @Test
  void test() {
    var compilation =
        getCompiler()
            .compile(
                JavaFileObjects.forSourceString(
                    "pkg.Foo",
                    /* language=java */
                    """
package pkg;

public class Foo {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Bar",
                    /* language=java */
                    """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class)
public interface Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile(
            "pkg.%sBar".formatted(GenerateKotlinAccessorsProcessor.GENERATED_CLASS_PREFIX))
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.%sBar".formatted(GenerateKotlinAccessorsProcessor.GENERATED_CLASS_PREFIX),
                    /* language=java */
                    """
package pkg;

%4$s // We don't really care about the metadata here, it'll be tested in the example project
public class %1$sBar {
  public static void bar(pkg.Foo $this$bar, %2$s<? super pkg.Bar> configure) {
    ((%3$s) $this$bar).getExtensions().configure("bar", configure);
  }

  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((%3$s) $this$bar).getExtensions().getByName("bar");
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.GENERATED_CLASS_PREFIX,
                        GenerateKotlinAccessorsProcessor.ACTION,
                        GenerateKotlinAccessorsProcessor.EXTENSION_AWARE,
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            /* language= */ "bar", "pkg/Bar", List.of("pkg/Foo")))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void missingKotlinModuleName() {
    var compilation =
        getCompiler()
            .withOptions()
            .compile(
                JavaFileObjects.forSourceString(
                    "pkg.Foo",
                    /* language=java */
                    """
package pkg;

public class Foo {}
"""));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_KOTLIN_MODULE_NAME);
  }

  @Test
  void badExtensionName() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bad-name", receivers = Foo.class)
public interface Bar {}
""");
    var compilation =
        getCompiler()
            .compile(
                JavaFileObjects.forSourceString(
                    "pkg.Foo",
                    /* language=java */
                    """
package pkg;

public class Foo {}
"""),
                sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_BAD_EXTENSION_NAME)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(33);
  }

  @Test
  void inexistantReceiver() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class)
public interface Bar {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(": class Foo")
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(52);
  }
}
