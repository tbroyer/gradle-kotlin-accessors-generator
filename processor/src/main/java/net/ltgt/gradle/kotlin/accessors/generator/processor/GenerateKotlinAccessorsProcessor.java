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

import static java.lang.Character.isISOControl;
import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import kotlin.Metadata;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmClassifier;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmPackage;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmType;
import kotlin.metadata.KmTypeProjection;
import kotlin.metadata.KmValueParameter;
import kotlin.metadata.KmVariance;
import kotlin.metadata.Visibility;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.JvmMethodSignature;
import kotlin.metadata.jvm.KmModule;
import kotlin.metadata.jvm.KmPackageParts;
import kotlin.metadata.jvm.KotlinClassMetadata;
import kotlin.metadata.jvm.KotlinModuleMetadata;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;
import net.ltgt.gradle.kotlin.accessors.generator.GenerateKotlinAccessors;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@AutoService(Processor.class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.AGGREGATING)
public class GenerateKotlinAccessorsProcessor extends AbstractProcessor {

  private static final String ANNOTATION_NAME = GenerateKotlinAccessors.class.getCanonicalName();
  private static final String ANNOTATION_SIMPLE_NAME =
      GenerateKotlinAccessors.class.getSimpleName();
  private static final JvmMetadataVersion JVM_METADATA_VERSION = new JvmMetadataVersion(1, 4);

  @VisibleForTesting
  static final String KOTLIN_MODULE_NAME =
      "net.ltgt.gradle.kotlin.accessors.generator.kotlinModuleName";

  @VisibleForTesting static final String GENERATED_CLASS_PREFIX = "$GradleKotlinAccessors$";
  @VisibleForTesting static final String EXTENSION_AWARE = "org.gradle.api.plugins.ExtensionAware";
  @VisibleForTesting static final String ACTION = "org.gradle.api.Action";

  @VisibleForTesting
  static final String ERROR_MISSING_KOTLIN_MODULE_NAME =
      KOTLIN_MODULE_NAME + " option must be supplied";

  @VisibleForTesting
  static final String ERROR_BAD_EXTENSION_NAME =
      ANNOTATION_SIMPLE_NAME + ".name is not a valid identifier";

  @VisibleForTesting
  static final String ERROR_PRIVATE_EXTENSION_NAME =
      ANNOTATION_SIMPLE_NAME + ".name must not start with an underscore";

  private @Nullable String kotlinModuleName;
  private final Map<String, List<String>> packages = new LinkedHashMap<>();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(ANNOTATION_NAME);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return Collections.singleton(KOTLIN_MODULE_NAME);
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    kotlinModuleName = processingEnv.getOptions().get(KOTLIN_MODULE_NAME);
    if (kotlinModuleName == null) {
      fatalError(ERROR_MISSING_KOTLIN_MODULE_NAME);
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      processImpl(roundEnv);
    } catch (Exception e) {
      // We don't allow exceptions of any kind to propagate to the compiler
      StringWriter writer = new StringWriter();
      e.printStackTrace(new PrintWriter(writer));
      fatalError(writer.toString());
    }
    return false;
  }

  private void processImpl(RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      generateKotlinModuleFiles();
    } else {
      processAnnotations(roundEnv);
    }
  }

  private void processAnnotations(RoundEnvironment roundEnv) {
    for (Element e : roundEnv.getElementsAnnotatedWith(GenerateKotlinAccessors.class)) {
      if (!checkAnnotatedElement(e)) {
        continue;
      }
      AnnotationMirror annotation = getAnnotationMirror(e);
      String extensionName = getExtensionName(e, annotation);
      if (extensionName == null) {
        continue;
      }
      List<? extends TypeMirror> receivers = getReceivers(annotation);
      if (receivers == null) {
        continue;
      }
      String className =
          generateKotlinExtensions(
              (TypeElement) e,
              extensionName,
              receivers.stream()
                  .map(processingEnv.getTypeUtils()::asElement)
                  .map(TypeElement.class::cast)
                  .collect(Collectors.toList()));
      packages
          .computeIfAbsent(
              processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString(),
              ignored -> new ArrayList<>())
          .add(className);
    }
  }

  private boolean checkAnnotatedElement(Element e) {
    switch (e.getKind()) {
      case CLASS:
      case INTERFACE:
      case ENUM:
        // case RECORD: // handled below
        return true;
      default:
        if ("RECORD".equals(e.getKind().name())) {
          return true;
        }
        // Let JavaC emit the error when checking the @Target(TYPE)
        return false;
    }
  }

  private @Nullable String getExtensionName(Element e, AnnotationMirror annotation) {
    AnnotationValue annotationValue = getAnnotationValue(annotation, "name");
    if (annotationValue == null || !(annotationValue.getValue() instanceof String)) {
      // Let JavaC emit the error for the missing attribute or bad type
      return null;
    }
    String extensionName = (String) annotationValue.getValue();
    if (!SourceVersion.isIdentifier(extensionName)
        || SourceVersion.isKeyword(extensionName /*, processingEnv.getSourceVersion()*/)) {
      processingEnv
          .getMessager()
          .printMessage(Kind.ERROR, ERROR_BAD_EXTENSION_NAME, e, annotation, annotationValue);
      return null;
    }
    if (extensionName.startsWith("_")) {
      processingEnv
          .getMessager()
          .printMessage(Kind.ERROR, ERROR_PRIVATE_EXTENSION_NAME, e, annotation, annotationValue);
      return null;
    }
    return extensionName;
  }

  private String generateKotlinExtensions(
      TypeElement e, String extensionName, List<? extends TypeElement> receivers) {
    String packageName =
        processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString();
    String name = GENERATED_CLASS_PREFIX + e.getSimpleName();
    String getterName =
        "get" + Character.toUpperCase(extensionName.charAt(0)) + extensionName.substring(1);

    TypeMirror extensionAwareType =
        processingEnv.getElementUtils().getTypeElement(EXTENSION_AWARE).asType();
    String extensionAware =
        processingEnv.getTypeUtils().isSubtype(e.asType(), extensionAwareType)
            ? "$this$" + extensionName
            : "((" + EXTENSION_AWARE + ") $this$" + extensionName + ")";
    try {
      JavaFileObject javaFileObject =
          processingEnv.getFiler().createSourceFile(packageName + "." + name, e);
      try (PrintWriter out = new PrintWriter(javaFileObject.openWriter())) {
        out.println("package " + packageName + ";");
        out.println();
        out.println(
            generateKotlinMetadata(
                extensionName,
                className(e),
                className(processingEnv.getElementUtils().getBinaryName(e).toString()),
                receivers.stream()
                    .map(receiver -> Receiver.create(receiver, processingEnv))
                    .collect(Collectors.toList()),
                getterName));
        out.println("@org.gradle.api.Generated");
        out.println("public class " + name + " {");
        for (TypeElement receiver : receivers) {
          out.printf(
              Locale.ROOT,
              "\n"
                  + "  public static void %1$s(%2$s $this$%1$s, %3$s<? super %4$s> configure) {\n"
                  + "    %5$s.getExtensions().configure(\"%1$s\", configure);\n"
                  + "  }\n"
                  + "\n"
                  + "  public static %4$s %6$s(%2$s $this$%1$s) {\n"
                  + "    return (%4$s) %5$s.getExtensions().getByName(\"%1$s\");\n"
                  + "  }\n",
              extensionName,
              receiver.getQualifiedName(),
              ACTION,
              e.getQualifiedName(),
              extensionAware,
              getterName);
        }
        out.println("}");
      }
    } catch (IOException ioe) {
      fatalError("Unable to create " + packageName + "." + name + ", " + ioe);
    }
    return name;
  }

  @VisibleForTesting
  static String generateKotlinMetadata(
      String extensionName,
      String element,
      String elementSignatureName,
      List<Receiver> receivers,
      String getterName) {
    KmType elementType = new KmType();
    elementType.setClassifier(new KmClassifier.Class(element));

    KmPackage kmPackage = new KmPackage();
    for (Receiver receiver : receivers) {
      KmType receiverType = new KmType();
      receiverType.setClassifier(new KmClassifier.Class(receiver.kotlinClassName()));

      KmFunction fun = new KmFunction(extensionName);
      Attributes.setVisibility(fun, Visibility.PUBLIC);
      fun.setReceiverParameterType(receiverType);
      KmType actionType = new KmType();
      actionType.setClassifier(new KmClassifier.Class(className(ACTION)));
      actionType.getArguments().add(new KmTypeProjection(KmVariance.IN, elementType));
      KmValueParameter param = new KmValueParameter("configure");
      param.setType(actionType);
      fun.getValueParameters().add(param);
      KmType voidType = new KmType();
      voidType.setClassifier(new KmClassifier.Class("kotlin/Unit"));
      fun.setReturnType(voidType);
      JvmExtensionsKt.setSignature(
          fun,
          new JvmMethodSignature(
              extensionName,
              String.format("(L%s;L%s)V", receiver.signatureClassName(), className(ACTION))));
      kmPackage.getFunctions().add(fun);

      KmProperty prop = new KmProperty(0, extensionName, 0, 0);
      Attributes.setVisibility(prop, Visibility.PUBLIC);
      Attributes.setVisibility(prop.getGetter(), Visibility.PUBLIC);
      Attributes.setNotDefault(prop.getGetter(), true);
      prop.setReceiverParameterType(receiverType);
      prop.setReturnType(elementType);
      JvmExtensionsKt.setGetterSignature(
          prop,
          new JvmMethodSignature(
              getterName,
              String.format("(L%s;)L%s;", receiver.signatureClassName(), elementSignatureName)));
      kmPackage.getProperties().add(prop);
    }
    Metadata metadata =
        new KotlinClassMetadata.FileFacade(kmPackage, JVM_METADATA_VERSION, 0).write();
    return String.format(
        Locale.ROOT,
        "@kotlin.Metadata(\n"
            + "    k = %d,\n"
            + "    mv = { %s },\n"
            + "    d1 = { %s },\n"
            + "    d2 = { %s }\n"
            + ")\n",
        metadata.k(),
        Arrays.stream(metadata.mv()).mapToObj(Integer::toString).collect(Collectors.joining(", ")),
        Arrays.stream(metadata.d1())
            .map(GenerateKotlinAccessorsProcessor::escape)
            .collect(Collectors.joining(", ")),
        Arrays.stream(metadata.d2())
            .map(GenerateKotlinAccessorsProcessor::escape)
            .collect(Collectors.joining(", ")));
  }

  private static String className(TypeElement e) {
    if (requireNonNull(e.getEnclosingElement()).getKind() == ElementKind.PACKAGE) {
      return e.getQualifiedName().toString().replace('.', '/');
    }
    return className((TypeElement) e.getEnclosingElement()) + "." + e.getSimpleName();
  }

  private static String className(String topLevelName) {
    return topLevelName.replace('.', '/');
  }

  @SuppressWarnings("unchecked")
  private static @Nullable List<? extends TypeMirror> getReceivers(AnnotationMirror annotation) {
    AnnotationValue receivers = getAnnotationValue(annotation, "receivers");
    if (receivers == null || !(receivers.getValue() instanceof List)) {
      // Let JavaC emit the error for the missing attribute or bad type
      return null;
    }
    try {
      // TODO: check receivers; possibly defer generation if there's an ErrorType
      return ((List<? extends AnnotationValue>) receivers.getValue())
          .stream()
              .map(AnnotationValue::getValue)
              .map(TypeMirror.class::cast)
              .collect(Collectors.toList());
    } catch (ClassCastException ignored) {
      // Let JavaC emit the error for the bad type
      return null;
    }
  }

  static String escape(String value) {
    return value
        .chars()
        .mapToObj(
            c -> {
              switch (c) {
                case '"':
                case '\\':
                  return "\\" + Character.toString((char) c);
                case '\n':
                  return "\\n";
                case '\r':
                  return "\\r";
                case '\t':
                  return "\\t";
                default:
                  return isISOControl(c)
                      ? String.format(Locale.ROOT, "\\u%04x", c)
                      : Character.toString((char) c);
              }
            })
        .collect(Collectors.joining("", "\"", "\""));
  }

  private void generateKotlinModuleFiles() {
    Filer filer = processingEnv.getFiler();
    String resourceFile = "META-INF/" + requireNonNull(kotlinModuleName) + ".kotlin_module";
    try {
      FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
      try (OutputStream out = fileObject.openOutputStream()) {
        KmModule kotlinModule = new KmModule();
        packages.forEach(
            (packageName, classNames) ->
                kotlinModule
                    .getPackageParts()
                    .put(
                        packageName,
                        new KmPackageParts(
                            classNames.stream()
                                .map(className -> className(packageName + "." + className))
                                .collect(Collectors.toList()),
                            Collections.emptyMap())));
        out.write(new KotlinModuleMetadata(kotlinModule, JVM_METADATA_VERSION).write());
      }
    } catch (IOException e) {
      fatalError("Unable to create " + resourceFile + ", " + e);
    }
  }

  private static AnnotationMirror getAnnotationMirror(Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(
            annotationMirror ->
                ((TypeElement) annotationMirror.getAnnotationType().asElement())
                    .getQualifiedName()
                    .contentEquals(ANNOTATION_NAME))
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }

  private static @Nullable AnnotationValue getAnnotationValue(
      AnnotationMirror annotation, String value) {
    return annotation.getElementValues().entrySet().stream()
        .filter(entry -> entry.getKey().getSimpleName().contentEquals(value))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private void fatalError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
  }

  @AutoValue
  abstract static class Receiver {
    static Receiver create(String kotlinClassName, String signatureClassName) {
      return new AutoValue_GenerateKotlinAccessorsProcessor_Receiver(
          kotlinClassName, signatureClassName);
    }

    static Receiver create(TypeElement element, ProcessingEnvironment processingEnv) {
      return create(
          className(element),
          className(processingEnv.getElementUtils().getBinaryName(element).toString()));
    }

    abstract String kotlinClassName();

    abstract String signatureClassName();
  }
}
