# gradle-kotlin-accessors-generator

An annotation processor, and its companion annotation, to generate type-safe Kotlin DSL accessors for your Gradle plugin's extensions.

Gradle generates such accessors automatically for extensions that are directly attached to a project (along with tasks and configurations), but not for extensions added to tasks or to other extensions.

For those other cases, plugin authors have to provide Kotlin extension functions and properties that users will import in their build scripts.
When you write your plugin in Kotlin, you can easily declare them:

```kotlin
val SomeTask.myExtension: MyExtension
    get() = (this as ExtensionAware).extensions.getByName("myExtension") as MyExtension

fun SomeTask.myExtension(action: Action<in MyExtension>) =
    (this as ExtensionAware).extensions.configure("myExtension", action)
```

but when you write it in Java, this forces you to add Kotlin to your project only to provide those accessors, and as a result deal with version compatibilities between Kotlin, Gradle and the JDK (tl;dr: this prevents you supporting older Gradle versions, or using newer Kotlin and Gradle versions to build your plugin).

This annotation processor is meant to "fix" that by generating the accessors from plain Java code.

## Compatibility

The annotation processor requires **JDK 8** at a minimum.

The generated code is compatible with Java 8, and the Kotlin metadata targets Kotlin 1.4.

Overall, this makes the processor and the generated code compatible down to **Gradle 6.8**.

## Usage

1. Add the `annotations` library as a `compileOnly` dependency, and the `processor` to your annotation processor path:

    ```kotlin
    dependencies {
        compileOnly("net.ltgt.gradle.kotlin-accessors-generator:annotations:${gkag.version}")
        annotationProcessor("net.ltgt.gradle.kotlin-accesors-generator:processor:${gkag.version}")
    }
    ```

2. Configure a Kotlin module name in your `compileJava` task; the Kotlin plugin automatically configures it to the project name so you can use just that:

    ```kotlin
    tasks {
        compileJava {
            options.compilerArgs.add("-Anet.ltgt.gradle.kotlin.accessors.generator.kotlinModuleName=${project.name}")
        }
    }
    ```

3. Annotate any type (or even a package-info) with `@GenerateKotlinAccessors`, providing the name of the class to generate, and information about your extensions: their name (I suggest using a constant), the public type of the extension, and the types of the objects your extension is added to:

    ```java
    public abstract class MyExtension {
        static final String NAME = "myExtension";
        // …
    ```

    ```java
    package import com.example.myPlugin;
    
    // imports elided
    
    @GenerateKotlinAccessors(
        className = "MyPluginKt",
        extensions = {
            @Extension(
                name = MyExtension.NAME,
                extensions = MyExtension.class,
                extended = SomeTask.class)
    })
    public class MyPlugin implements Plugin<Project> {
        // …
    ```

This will generate a `META-INF/${project.name}.kotlin_module` file, and for each annotated type a class (in this example `MyPluginKt`) in the same package with static methods implementing the accessors and the appropriate `@kotlin.Metadata` annotation to make those methods actually usable as Kotlin extensions. +
Note that the generated class (whose name is configured in `className`) is part of your plugin's public API as it will be directly referenced by binary plugin using your accessors (e.g. plugins written in Kotlin, or as Precompiled Kotlin Plugins).

Users of your plugin will then import the accessors the exact same way as if you had written them in Kotlin:

```kotlin
import com.example.myPlugin.myExtension

plugins {
    id("com.example.myPlugin")
}

tasks {
    withType<SomeTask> {
        myExtension.someProp = "some value"
        // — or —
        myExtension {
            someProp = "some value"
        }
    }
}
```

## Limitations

While Kotlin allows developers to declare functions and properties with names containing spaces or hyphens, this annotation processor only accepts extension names that are valid Java identifiers.
