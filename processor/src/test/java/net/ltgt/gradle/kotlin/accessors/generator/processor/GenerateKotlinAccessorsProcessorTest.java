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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.processor.GenerateKotlinAccessorsProcessor.Receiver;
import org.jetbrains.annotations.Nullable;
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
@org.gradle.api.Generated
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
                            /* language= */ "bar",
                            "pkg/Bar",
                            "pkg/Bar",
                            List.of(Receiver.create("pkg/Foo", "pkg/Foo")),
                            "getBar"))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void generatedClassName() {
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

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class, generatedClassName = "BarKt")
public interface Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("pkg.BarKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.BarKt",
                    /* language=java */
                    """
package pkg;

%3$s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class BarKt {
  public static void bar(pkg.Foo $this$bar, %1$s<? super pkg.Bar> configure) {
    ((%2$s) $this$bar).getExtensions().configure("bar", configure);
  }

  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((%2$s) $this$bar).getExtensions().getByName("bar");
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.ACTION,
                        GenerateKotlinAccessorsProcessor.EXTENSION_AWARE,
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            /* language= */ "bar",
                            "pkg/Bar",
                            "pkg/Bar",
                            List.of(Receiver.create("pkg/Foo", "pkg/Foo")),
                            "getBar"))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void duplicatedReceiver() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = { Foo.class, Foo.class })
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
    assertThat(compilation).succeeded();
    assertThat(compilation).hadWarningCount(1);
    assertThat(compilation)
        .hadWarningContaining(GenerateKotlinAccessorsProcessor.WARNING_DUPLICATE_VALUE)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(68);
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
@org.gradle.api.Generated
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
                            /* language= */ "bar",
                            "pkg/Bar",
                            "pkg/Bar",
                            List.of(Receiver.create("pkg/Foo", "pkg/Foo")),
                            "getBar"))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void extensionAware_extension() {
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
import org.gradle.api.plugins.ExtensionAware;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class)
public interface Bar extends ExtensionAware {}
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
@org.gradle.api.Generated
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
                            /* language= */ "bar",
                            "pkg/Bar",
                            "pkg/Bar",
                            List.of(Receiver.create("pkg/Foo", "pkg/Foo")),
                            "getBar"))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void nestedReceiver() {
    var compilation =
        getCompiler()
            .compile(
                JavaFileObjects.forSourceString(
                    "pkg.Foo",
                    /* language=java */
                    """
package pkg;

public class Foo {
  public static class Nested {}
}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Bar",
                    /* language=java */
                    """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.Nested.class)
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
@org.gradle.api.Generated
public class %1$sBar {
  public static void bar(pkg.Foo.Nested $this$bar, %2$s<? super pkg.Bar> configure) {
    ((%3$s) $this$bar).getExtensions().configure("bar", configure);
  }

  public static pkg.Bar getBar(pkg.Foo.Nested $this$bar) {
    return (pkg.Bar) ((%3$s) $this$bar).getExtensions().getByName("bar");
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.GENERATED_CLASS_PREFIX,
                        GenerateKotlinAccessorsProcessor.ACTION,
                        GenerateKotlinAccessorsProcessor.EXTENSION_AWARE,
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            /* language= */ "bar",
                            "pkg/Bar",
                            "pkg/Bar",
                            List.of(Receiver.create("pkg/Foo.Nested", "pkg/Foo$Nested")),
                            "getBar"))));
    // XXX: check content (?)
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
  void badExtensionName_private() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "_privateName", receivers = Foo.class)
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
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_PRIVATE_EXTENSION_NAME)
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
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_RECEIVER_TYPE)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(55);
    assertThat(compilation)
        .hadErrorContaining(": class Foo")
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(52);
  }

  @Test
  void generatedReceiver() {
    class TestProcessor extends AbstractProcessor {
      private @Nullable JavaFileObject generated;

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(GenerateKotlinAccessors.class.getCanonicalName());
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated == null) {
          try {
            generated = processingEnv.getFiler().createSourceFile("pkg.Foo");
            try (var out = generated.openWriter()) {
              out.write(
                  /* language=java */
                  """
package pkg;

public class Foo {}
""");
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        return false;
      }
    }

    var compilation =
        getCompiler()
            .withProcessors(new GenerateKotlinAccessorsProcessor(), new TestProcessor())
            .compile(
                JavaFileObjects.forSourceString(
                    "pkg.Bar",
                    /* language=java */
                    """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class)
public class Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedSourceFile("pkg.Foo");
    assertThat(compilation)
        .generatedSourceFile(
            "pkg." + GenerateKotlinAccessorsProcessor.GENERATED_CLASS_PREFIX + "Bar");
  }

  @Test
  void generatedReceiver_whenProcessingOver() {
    class TestProcessor extends AbstractProcessor {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(GenerateKotlinAccessors.class.getCanonicalName());
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
          try (var out = processingEnv.getFiler().createSourceFile("pkg.Foo").openWriter()) {
            out.write(
                /* language=java */
                """
package pkg;

public class Foo {}
""");
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        return false;
      }
    }

    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo.class)
public class Bar {}
""");
    var compilation =
        getCompiler()
            .withProcessors(new TestProcessor(), new GenerateKotlinAccessorsProcessor())
            .compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_RECEIVER_TYPE)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(55);
  }

  @Test
  void emptyReceivers() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = {})
public class Bar {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_NO_RECEIVERS)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(52);
  }

  @Test
  void arrayReceiver() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(name = "bar", receivers = Foo[].class)
public class Bar {}
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
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_ARRAY_RECEIVER)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(57);
  }
}
