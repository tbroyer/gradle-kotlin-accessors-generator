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
import net.ltgt.gradle.kotlin.accessors.generator.processor.GenerateKotlinAccessorsProcessor.Extension;
import net.ltgt.gradle.kotlin.accessors.generator.processor.GenerateKotlinAccessorsProcessor.Type;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

@SuppressWarnings("BadImport")
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public interface Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("pkg.KotlinExtensionsKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.KotlinExtensionsKt",
                    /* language=java */
                    """
package pkg;

%s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class KotlinExtensionsKt {
  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().getByName("bar");
  }

  public static void bar(pkg.Foo $this$bar, org.gradle.api.Action<? super pkg.Bar> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().configure("bar", action);
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            List.of(
                                Extension.create(
                                    "bar",
                                    Type.create("pkg.Bar", "pkg/Bar"),
                                    Set.of(Type.create("pkg.Foo", "pkg/Foo"))))))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void packageInfo() { // Also tests several extensions
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

public class Bar {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Baz",
                    /* language=java */
                    """
package pkg;

public class Baz {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Qux",
                    /* language=java */
                    """
package pkg;

public class Qux {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.package-info",
                    /* language=java */
                    """
@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class),
    @Extension(name = "qux", extension = Qux.class, extended = Baz.class)
})
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("pkg.KotlinExtensionsKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.KotlinExtensionsKt",
                    /* language=java */
                    """
package pkg;

%s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class KotlinExtensionsKt {
  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().getByName("bar");
  }

  public static void bar(pkg.Foo $this$bar, org.gradle.api.Action<? super pkg.Bar> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().configure("bar", action);
  }

  public static pkg.Qux getQux(pkg.Baz $this$qux) {
    return (pkg.Qux) ((org.gradle.api.plugins.ExtensionAware) $this$qux).getExtensions().getByName("qux");
  }

  public static void qux(pkg.Baz $this$qux, org.gradle.api.Action<? super pkg.Qux> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$qux).getExtensions().configure("qux", action);
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            List.of(
                                Extension.create(
                                    "bar",
                                    Type.create("pkg.Bar", "pkg/Bar"),
                                    Set.of(Type.create("pkg.Foo", "pkg/Foo"))),
                                Extension.create(
                                    "qux",
                                    Type.create("pkg.Qux", "pkg/Qux"),
                                    Set.of(Type.create("pkg.Baz", "pkg/Baz"))))))));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/foo.kotlin_module");
  }

  @Test
  void duplicateClassName() { // Also tests several extensions
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public class Bar {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Baz",
                    /* language=java */
                    """
package pkg;

public class Baz {}
"""),
                JavaFileObjects.forSourceString(
                    "pkg.Qux",
                    /* language=java */
                    """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "qux", extension = Qux.class, extended = Baz.class)
})
public class Qux {}
"""));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Unable to create pkg.KotlinExtensionsKt,");
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = { Foo.class, Foo.class })
})
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
        .onLine(7)
        .atColumn(80);
    assertThat(compilation)
        .generatedSourceFile("pkg.KotlinExtensionsKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.KotlinExtensionsKt",
                    /* language=java */
                    """
package pkg;

%s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class KotlinExtensionsKt {
  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().getByName("bar");
  }

  public static void bar(pkg.Foo $this$bar, org.gradle.api.Action<? super pkg.Bar> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().configure("bar", action);
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            List.of(
                                Extension.create(
                                    "bar",
                                    Type.create("pkg.Bar", "pkg/Bar"),
                                    Set.of(Type.create("pkg.Foo", "pkg/Foo"))))))));
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;
import org.gradle.api.plugins.ExtensionAware;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public interface Bar extends ExtensionAware {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("pkg.KotlinExtensionsKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.KotlinExtensionsKt",
                    /* language=java */
                    """
package pkg;

%s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class KotlinExtensionsKt {
  public static pkg.Bar getBar(pkg.Foo $this$bar) {
    return (pkg.Bar) ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().getByName("bar");
  }

  public static void bar(pkg.Foo $this$bar, org.gradle.api.Action<? super pkg.Bar> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().configure("bar", action);
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            List.of(
                                Extension.create(
                                    "bar",
                                    Type.create("pkg.Bar", "pkg/Bar"),
                                    Set.of(Type.create("pkg.Foo", "pkg/Foo"))))))));
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.Nested.class)
})
public interface Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("pkg.KotlinExtensionsKt")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceString(
                "pkg.KotlinExtensionsKt",
                    /* language=java */
                    """
package pkg;

%s // We don't really care about the metadata here, it'll be tested in the example project
@org.gradle.api.Generated
public class KotlinExtensionsKt {
  public static pkg.Bar getBar(pkg.Foo.Nested $this$bar) {
    return (pkg.Bar) ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().getByName("bar");
  }

  public static void bar(pkg.Foo.Nested $this$bar, org.gradle.api.Action<? super pkg.Bar> action) {
    ((org.gradle.api.plugins.ExtensionAware) $this$bar).getExtensions().configure("bar", action);
  }
}
"""
                    .formatted(
                        GenerateKotlinAccessorsProcessor.generateKotlinMetadata(
                            List.of(
                                Extension.create(
                                    "bar",
                                    Type.create("pkg.Bar", "pkg/Bar"),
                                    Set.of(Type.create("pkg.Foo.Nested", "pkg/Foo.Nested"))))))));
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
  void badClassName() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "bad-name", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
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
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_BAD_CLASS_NAME)
        .inFile(sourceFile)
        .onLine(6)
        .atColumn(38);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bad-name", extension = Bar.class, extended = Foo.class)
})
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
        .onLine(7)
        .atColumn(23);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "_privateName", extension = Bar.class, extended = Foo.class)
})
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
        .onLine(7)
        .atColumn(23);
  }

  @Test
  void inexistantExtension() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Foo",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public interface Foo {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_TYPE)
        .inFile(sourceFile)
        .onLine(6)
        .atColumn(73);
    assertThat(compilation)
        .hadErrorContaining(": class Bar")
        .inFile(sourceFile)
        .onLine(7)
        .atColumn(42);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public interface Bar {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_TYPE)
        .inFile(sourceFile)
        .onLine(6)
        .atColumn(73);
    assertThat(compilation)
        .hadErrorContaining(": class Foo")
        .inFile(sourceFile)
        .onLine(7)
        .atColumn(64);
  }

  @Test
  void generatedExtension() {
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
            generated = processingEnv.getFiler().createSourceFile("pkg.Bar");
            try (var out = generated.openWriter()) {
              out.write(
                  /* language=java */
                  """
package pkg;

public class Bar {}
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
                    "pkg.Foo",
                    /* language=java */
                    """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public class Foo {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedSourceFile("pkg.Bar");
    assertThat(compilation).generatedSourceFile("pkg.KotlinExtensionsKt");
  }

  @Test
  void generatedExtension_whenProcessingOver() {
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
          try (var out = processingEnv.getFiler().createSourceFile("pkg.Bar").openWriter()) {
            out.write(
                /* language=java */
                """
package pkg;

public class Bar {}
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
            "pkg.Foo",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public class Foo {}
""");
    var compilation =
        getCompiler()
            .withProcessors(new TestProcessor(), new GenerateKotlinAccessorsProcessor())
            .compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_TYPE)
        .inFile(sourceFile)
        .onLine(6)
        .atColumn(73);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public class Bar {}
"""));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedSourceFile("pkg.Foo");
    assertThat(compilation).generatedSourceFile("pkg.KotlinExtensionsKt");
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo.class)
})
public class Bar {}
""");
    var compilation =
        getCompiler()
            .withProcessors(new TestProcessor(), new GenerateKotlinAccessorsProcessor())
            .compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_MISSING_TYPE)
        .inFile(sourceFile)
        .onLine(6)
        .atColumn(73);
  }

  @Test
  void emptyExtensions() {
    var sourceFile =
        JavaFileObjects.forSourceString(
            "pkg.Bar",
            /* language=java */
            """
package pkg;

import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {})
public class Bar {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_EMPTY)
        .inFile(sourceFile)
        .onLine(5)
        .atColumn(73);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = {})
})
public class Bar {}
""");
    var compilation = getCompiler().compile(sourceFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_EMPTY)
        .inFile(sourceFile)
        .onLine(7)
        .atColumn(64);
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
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors.Extension;

@GenerateKotlinAccessors(className = "KotlinExtensionsKt", extensions = {
    @Extension(name = "bar", extension = Bar.class, extended = Foo[].class)
})
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
        .hadErrorContaining(GenerateKotlinAccessorsProcessor.ERROR_ARRAY_EXTENDED)
        .inFile(sourceFile)
        .onLine(7)
        .atColumn(69);
  }
}
