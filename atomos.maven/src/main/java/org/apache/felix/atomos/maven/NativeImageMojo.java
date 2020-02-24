/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.atomos.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.ReflectConfigUtil.ReflectConfig;
import org.apache.felix.atomos.maven.ResourceConfigUtil.ResourceConfigResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "atomos-native-image", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class NativeImageMojo extends AbstractMojo
{

    private static final String ATOMOS_PATH = "ATOMOS";

    @Parameter(defaultValue = "${project.build.directory}/" + ATOMOS_PATH)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/" + "classpath_lib")
    private File classpath_lib;

    @Parameter(defaultValue = "graal.native.image.build.args")
    private String nativeImageArgsPropertyName;

    @Parameter(defaultValue = "${project}", required = true, readonly = false)
    private MavenProject project;

    @Parameter
    private String mainClass;

    @Parameter(defaultValue = "false") //TODO: CHECK GRAAL EE ONLY
    private boolean debug;

    @Parameter
    private String imageName;

    @Parameter
    private Path nativeImageExecutable;

    @Parameter
    private List<String> additionalInitializeAtBuildTime;

    @Parameter
    private List<File> graalResourceConfigFiles;

    @Parameter
    private List<File> dynamicProxyConfigurationFiles;

    @Parameter
    private List<File> reflectConfigFiles;

    public static boolean isJarFile(Path path)
    {
        try (JarFile j = new JarFile(path.toFile());)
        {

            return true;
        }
        catch (final IOException e)
        {

        }

        return false;
    }

    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().info("outputDirectory" + outputDirectory);
        try
        {
            Files.createDirectories(outputDirectory.toPath());

            final Config config = new Config();
            config.outputDir = outputDirectory.toPath();
            config.mainClass = mainClass;
            config.additionalInitializeAtBuildTime = additionalInitializeAtBuildTime;
            if (imageName == null || imageName.isEmpty())
            {
                config.imageName = project.getArtifactId();
            }
            else
            {
                config.imageName = imageName;
            }

            if (graalResourceConfigFiles != null && !graalResourceConfigFiles.isEmpty())
            {
                config.resourceConfigs = graalResourceConfigFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            if (reflectConfigFiles != null && !reflectConfigFiles.isEmpty())
            {
                config.reflectConfigFiles = reflectConfigFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            if (dynamicProxyConfigurationFiles != null
                && !dynamicProxyConfigurationFiles.isEmpty())
            {
                config.dynamicProxyConfigurationFiles = dynamicProxyConfigurationFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            final List<Path> classPath = Files.list(classpath_lib.toPath()).filter(
                NativeImageMojo::isJarFile).collect(Collectors.toList());

            final Path substrateJar = SubstrateUtil.createSubstrateJar(classPath,
                outputDirectory.toPath());

            final Map<String, ReflectConfig> reflectConfigs = ReflectConfigUtil.reflectConfig(
                classPath, config);

            final ResourceConfigResult resourceConfigResult = ResourceConfigUtil.resourceConfig(
                classPath, config);

            final String content = ReflectConfigUtil.createConfigContent(reflectConfigs);
            if (!content.isEmpty())
            {
                final Path reflectConfig = config.outputDir.resolve(
                    "graal_reflect_config" + System.nanoTime() + ".json");
                Files.write(reflectConfig, content.getBytes());
                config.reflectConfigFiles.add(reflectConfig);
            }

            final List<String> args = NativeImageBuilder.createArgs(config,
                reflectConfigs, resourceConfigResult);

            classPath.add(substrateJar);

            final Optional<Path> exec = NativeImageBuilder.findNativeImageExecutable(
                nativeImageExecutable);

            if (exec.isEmpty())
            {
                throw new MojoExecutionException(
                    "Missing native image executable. Set 'GRAAL_VM' with the path as an environment variable");
            }

            NativeImageBuilder.execute(exec.get(), outputDirectory.toPath(), classPath,
                args);
        }
        catch (

            final Exception e)
        {
            throw new MojoExecutionException("Error", e);
        }

    }

    static class Config
    {

        public String mainClass;
        public String imageName;

        public List<String> additionalInitializeAtBuildTime = new ArrayList<>();
        public boolean debug = false;
        public List<Path> resourceConfigs = new ArrayList<>();
        public List<Path> dynamicProxyConfigurationFiles = new ArrayList<>();
        public List<Path> reflectConfigFiles = new ArrayList<>();
        public Path outputDir;
    }
}
