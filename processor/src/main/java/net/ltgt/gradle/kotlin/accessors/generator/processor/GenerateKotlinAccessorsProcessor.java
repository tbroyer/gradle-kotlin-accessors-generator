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
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
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

  @VisibleForTesting
  static final String ERROR_MISSING_KOTLIN_MODULE_NAME =
      KOTLIN_MODULE_NAME + " option must be supplied";

  @VisibleForTesting
  static final String ERROR_BAD_CLASS_NAME =
      ANNOTATION_SIMPLE_NAME + ".className is not a valid identifier";

  @VisibleForTesting
  static final String ERROR_BAD_EXTENSION_NAME =
      ANNOTATION_SIMPLE_NAME + ".Extension.name is not a valid identifier";

  @VisibleForTesting
  static final String ERROR_PRIVATE_EXTENSION_NAME =
      ANNOTATION_SIMPLE_NAME + ".Extension.name must not start with an underscore";

  @VisibleForTesting
  static final String ERROR_MISSING_TYPE =
      GenerateKotlinAccessorsProcessor.class.getCanonicalName()
          + " was unable to process this annotation because not all of the referenced types could be resolved.";

  @VisibleForTesting static final String ERROR_EMPTY = "Cannot be empty";

  @VisibleForTesting
  static final String ERROR_ARRAY_EXTENDED = "Extensions cannot be attached to arrays";

  @VisibleForTesting static final String WARNING_DUPLICATE_VALUE = "Duplicate value";

  private @Nullable String kotlinModuleName;
  private final Map<String, List<String>> packages = new LinkedHashMap<>();
  private final Set<Element> deferredElements = new LinkedHashSet<>();

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
    packages.clear();
    deferredElements.clear();
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
    processAnnotations(roundEnv);
    if (roundEnv.processingOver()) {
      if (!deferredElements.isEmpty()) {
        for (Element e : deferredElements) {
          AnnotationMirror annotation = getAnnotationMirror(e);
          processingEnv
              .getMessager()
              .printMessage(
                  Kind.ERROR,
                  ERROR_MISSING_TYPE,
                  e,
                  annotation,
                  getAnnotationValue(annotation.getElementValues(), "extensions"));
        }
      } else {
        generateKotlinModule();
      }
    }
  }

  private void processAnnotations(RoundEnvironment roundEnv) {
    Set<Element> elements = new LinkedHashSet<>();
    elements.addAll(roundEnv.getElementsAnnotatedWith(GenerateKotlinAccessors.class));
    elements.addAll(deferredElements);
    deferredElements.clear();
    for (Element e : elements) {
      // We don't care about the annotated element itself, the annotation is self-contained
      AnnotationMirror annotation = getAnnotationMirror(e);
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
          annotation.getElementValues();
      String className = getClassName(e, annotation, elementValues);
      if (className == null) {
        continue;
      }
      List<Extension> extensions = getExtensions(e, annotation, elementValues);
      if (extensions == null) {
        continue;
      }
      String packageName =
          processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString();
      generateKotlinAccessors(packageName, className, e, annotation, extensions);
      packages.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(className);
    }
  }

  private void generateKotlinAccessors(
      String packageName,
      String className,
      Element e,
      @SuppressWarnings("unused") AnnotationMirror annotation,
      List<Extension> extensions) {
    try {
      JavaFileObject javaFileObject =
          processingEnv.getFiler().createSourceFile(packageName + "." + className, e);
      try (Writer out = javaFileObject.openWriter()) {
        out.write("package " + packageName + ";\n\n");
        out.write(generateKotlinMetadata(extensions));
        out.write("@org.gradle.api.Generated\n");
        out.write("public class " + className + " {\n");
        for (Extension extension : extensions) {
          for (Type extended : extension.extended()) {
            out.write(
                String.format(
                    "\npublic static %3$s %2$s(%4$s $this$%1$s) {\n"
                        + "  return (%3$s) ((org.gradle.api.plugins.ExtensionAware) $this$%1$s).getExtensions().getByName(\"%1$s\");\n"
                        + "}\n"
                        + "\npublic static void %1$s(%4$s $this$%1$s, org.gradle.api.Action<? super %3$s> action) {\n"
                        + "  ((org.gradle.api.plugins.ExtensionAware) $this$%1$s).getExtensions().configure(\"%1$s\", action);\n"
                        + "}\n",
                    extension.name(),
                    extension.getterName(),
                    extension.extension().qualifiedName(),
                    extended.qualifiedName()));
          }
        }
        out.write("}\n");
      }
    } catch (IOException ioe) {
      fatalError("Unable to create " + packageName + "." + className + ", " + ioe);
    }
  }

  @VisibleForTesting
  static String generateKotlinMetadata(List<Extension> extensions) {
    KmType unitType = new KmType();
    unitType.setClassifier(new KmClassifier.Class("kotlin/Unit"));

    KmPackage kmPackage = new KmPackage();
    for (Extension extension : extensions) {
      KmType extensionType = new KmType();
      extensionType.setClassifier(new KmClassifier.Class(extension.extension().kotlinName()));
      for (Type receiver : extension.extended()) {
        KmType receiverType = new KmType();
        receiverType.setClassifier(new KmClassifier.Class(receiver.kotlinName()));

        KmProperty prop = new KmProperty(extension.name());
        Attributes.setVisibility(prop, Visibility.PUBLIC);
        Attributes.setVisibility(prop.getGetter(), Visibility.PUBLIC);
        Attributes.setNotDefault(prop.getGetter(), true);
        prop.setReceiverParameterType(receiverType);
        prop.setReturnType(extensionType);
        JvmExtensionsKt.setGetterSignature(
            prop,
            new JvmMethodSignature(
                extension.getterName(),
                String.format(
                    "(%s)%s", receiver.signatureName(), extension.extension().signatureName())));
        kmPackage.getProperties().add(prop);

        KmFunction fun = new KmFunction(extension.name());
        Attributes.setVisibility(fun, Visibility.PUBLIC);
        fun.setReceiverParameterType(receiverType);
        KmType actionType = new KmType();
        actionType.setClassifier(new KmClassifier.Class("org/gradle/api/Action"));
        actionType.getArguments().add(new KmTypeProjection(KmVariance.IN, extensionType));
        KmValueParameter param = new KmValueParameter("action");
        param.setType(actionType);
        fun.getValueParameters().add(param);
        fun.setReturnType(unitType);
        JvmExtensionsKt.setSignature(
            fun,
            new JvmMethodSignature(
                extension.name(),
                String.format(
                    "(%s%s)V", receiver.signatureName(), extension.extension().signatureName())));
        kmPackage.getFunctions().add(fun);
      }
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

  private static String escape(String value) {
    return value
        .chars()
        .mapToObj(
            c -> {
              switch (c) {
                case '"':
                case '\\':
                  return "\\" + (char) c;
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

  private void generateKotlinModule() {
    String resourceFile = "META-INF/" + requireNonNull(kotlinModuleName) + ".kotlin_module";
    try (OutputStream out =
        processingEnv
            .getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile)
            .openOutputStream()) {
      KmModule kotlinModule = new KmModule();
      packages.forEach(
          (packageName, classNames) ->
              kotlinModule
                  .getPackageParts()
                  .put(
                      packageName,
                      new KmPackageParts(
                          classNames.stream()
                              .map(className -> packageName.replace('.', '/') + "/" + className)
                              .collect(Collectors.toList()),
                          Collections.emptyMap())));
      out.write(new KotlinModuleMetadata(kotlinModule, JVM_METADATA_VERSION).write());
    } catch (IOException e) {
      fatalError("Unable to create " + resourceFile + ", " + e);
    }
  }

  private AnnotationMirror getAnnotationMirror(Element e) {
    return e.getAnnotationMirrors().stream()
        .filter(
            annotationMirror ->
                ((TypeElement) annotationMirror.getAnnotationType().asElement())
                    .getQualifiedName()
                    .contentEquals(ANNOTATION_NAME))
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }

  private @Nullable String getClassName(
      Element e,
      AnnotationMirror annotation,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return getJavaIdentifier(e, annotation, elementValues, "className", ERROR_BAD_CLASS_NAME, null);
  }

  private @Nullable List<Extension> getExtensions(
      Element e,
      AnnotationMirror annotation,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    AnnotationValue value = getAnnotationValue(elementValues, "extensions");
    if (value == null || !(value.getValue() instanceof List)) {
      // Let JavaC emit the error for the missing attribute or bad type
      return null;
    }
    @SuppressWarnings("unchecked")
    List<? extends AnnotationValue> values = ((List<? extends AnnotationValue>) value.getValue());
    if (values.isEmpty()) {
      processingEnv.getMessager().printMessage(Kind.ERROR, ERROR_EMPTY, e, annotation, value);
    }
    List<Extension> extensions = new ArrayList<>(values.size());
    for (AnnotationValue extension : values) {
      Object v = extension.getValue();
      if (!(v instanceof AnnotationMirror)) {
        // Let JavaC emit the error for the bad type
        return null;
      }
      elementValues = ((AnnotationMirror) v).getElementValues();
      String extensionName = getExtensionName(e, annotation, elementValues);
      Type extensionType = getExtensionType(e, annotation, elementValues);
      Set<Type> extendedTypes = getExtendedTypes(e, annotation, elementValues);
      if (extensionName == null || extensionType == null || extendedTypes == null) {
        return null;
      }
      extensions.add(Extension.create(extensionName, extensionType, extendedTypes));
    }
    return extensions;
  }

  private @Nullable String getExtensionName(
      Element e,
      AnnotationMirror annotation,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return getJavaIdentifier(
        e,
        annotation,
        elementValues,
        "name",
        ERROR_BAD_EXTENSION_NAME,
        name -> name.startsWith("_") ? ERROR_PRIVATE_EXTENSION_NAME : null);
  }

  private @Nullable Type getExtensionType(
      Element e,
      @SuppressWarnings("unused") AnnotationMirror annotation,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    AnnotationValue annotationValue = getAnnotationValue(elementValues, "extension");
    if (annotationValue == null) {
      // Let JavaC emit the error for the missing attribute
      return null;
    }
    Object v = annotationValue.getValue();
    if (!(v instanceof TypeMirror) || ((TypeMirror) v).getKind() != TypeKind.DECLARED) {
      // Either this is a malformed annotation or it references an inexistant class,
      // so defer processing, and JavaC might emit the error
      deferredElements.add(e);
      return null;
    }
    TypeMirror value = (TypeMirror) annotationValue.getValue();
    return Type.create((TypeElement) processingEnv.getTypeUtils().asElement(value));
  }

  private @Nullable Set<Type> getExtendedTypes(
      Element e,
      AnnotationMirror annotation,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    AnnotationValue extended = getAnnotationValue(elementValues, "extended");
    if (extended == null || !(extended.getValue() instanceof List)) {
      // Let JavaC emit the error for the missing attribute or bad type
      return null;
    }
    Set<Type> elements = new LinkedHashSet<>();
    @SuppressWarnings("unchecked")
    List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) extended.getValue();
    if (values.isEmpty()) {
      processingEnv.getMessager().printMessage(Kind.ERROR, ERROR_EMPTY, e, annotation, extended);
    }
    for (AnnotationValue annotationValue : values) {
      Object value = annotationValue.getValue();
      if (!(value instanceof TypeMirror)) {
        // Either this is a malformed annotation or it references an inexistant class,
        // so defer processing, and JavaC might emit the error
        deferredElements.add(e);
        return null;
      }
      TypeMirror typeMirror = (TypeMirror) value;
      switch (typeMirror.getKind()) {
        case ERROR:
          deferredElements.add(e);
          return null;
        case DECLARED:
          break;
        case ARRAY:
          processingEnv
              .getMessager()
              .printMessage(Kind.ERROR, ERROR_ARRAY_EXTENDED, e, annotation, annotationValue);
          return null;
        default:
          // Let JavaC emit the error for the bad type
          return null;
      }
      TypeElement element = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
      if (!elements.add(Type.create(element))) {
        processingEnv
            .getMessager()
            .printMessage(Kind.WARNING, WARNING_DUPLICATE_VALUE, e, annotation, annotationValue);
      }
    }
    return elements;
  }

  private @Nullable String getJavaIdentifier(
      Element e,
      AnnotationMirror errorAnchor,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues,
      String name,
      String error,
      @Nullable Function<String, @Nullable String> verifier) {
    AnnotationValue annotationValue = getAnnotationValue(elementValues, name);
    if (annotationValue == null || !(annotationValue.getValue() instanceof String)) {
      // Let JavaC emit the error for the missing attribute or bad type
      return null;
    }
    String value = (String) annotationValue.getValue();
    if (!SourceVersion.isIdentifier(value)
        || SourceVersion.isKeyword(value /*, processingEnv.getSourceVersion()*/)) {
      processingEnv.getMessager().printMessage(Kind.ERROR, error, e, errorAnchor, annotationValue);
      return null;
    }
    if (verifier != null) {
      error = verifier.apply(value);
      if (error != null) {
        processingEnv
            .getMessager()
            .printMessage(Kind.ERROR, error, e, errorAnchor, annotationValue);
        return null;
      }
    }
    return value;
  }

  private @Nullable AnnotationValue getAnnotationValue(
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues, String value) {
    return elementValues.entrySet().stream()
        .filter(entry -> entry.getKey().getSimpleName().contentEquals(value))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private void fatalError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
  }

  @AutoValue
  abstract static class Extension {
    @VisibleForTesting
    static Extension create(String name, Type extension, Set<Type> extended) {
      return new AutoValue_GenerateKotlinAccessorsProcessor_Extension(name, extension, extended);
    }

    abstract String name();

    String getterName() {
      return "get" + name().substring(0, 1).toUpperCase(Locale.ENGLISH) + name().substring(1);
    }

    abstract Type extension();

    @SuppressWarnings("AutoValueImmutableFields")
    abstract Set<Type> extended();
  }

  @AutoValue
  abstract static class Type {
    private static Type create(TypeElement e) {
      return create(e.getQualifiedName().toString(), kotlinName(e));
    }

    @VisibleForTesting
    static Type create(String qualifiedName, String kotlinName) {
      return new AutoValue_GenerateKotlinAccessorsProcessor_Type(qualifiedName, kotlinName);
    }

    private static String kotlinName(TypeElement e) {
      if (requireNonNull(e.getEnclosingElement()).getKind() == ElementKind.PACKAGE) {
        return e.getQualifiedName().toString().replace('.', '/');
      }
      return kotlinName((TypeElement) e.getEnclosingElement()) + "." + e.getSimpleName();
    }

    abstract String qualifiedName();

    abstract String kotlinName();

    String signatureName() {
      return "L" + kotlinName().replace('.', '$') + ";";
    }
  }
}
