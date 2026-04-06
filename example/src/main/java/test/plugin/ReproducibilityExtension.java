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
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;

@GenerateKotlinAccessors(
    name = ReproducibilityExtension.NAME,
    receivers = {Zip.class, Tar.class})
public class ReproducibilityExtension {
  public static final String NAME = "reproducibility";

  public void configureReproducibility() {}
}
