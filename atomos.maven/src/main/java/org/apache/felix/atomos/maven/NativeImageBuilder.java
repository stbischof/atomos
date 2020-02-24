/**
 *
 */
package org.apache.felix.atomos.maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.maven.NativeImageMojo.Config;
import org.apache.felix.atomos.maven.ReflectConfigUtil.ReflectConfig;
import org.apache.felix.atomos.maven.ResourceConfigUtil.ResourceConfigResult;
import org.apache.maven.plugin.MojoExecutionException;

public class NativeImageBuilder
{

    public static void execute(Config config, List<Path> classpath, List<String> args)
        throws MojoExecutionException
    {
        try
        {
            final Optional<Path> exec = findNativeImageExecutable(config);

            if (exec.isEmpty())
            {
                throw new MojoExecutionException(
                    "Missing native image executable. Set 'GRAAL_VM' with the path as an environment variable");
            }

            final String cp = classpath.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(":"));

            final List<String> commands = new ArrayList<>();
            commands.add(exec.get().toAbsolutePath().toString());
            commands.add("-cp");
            commands.add(cp);
            commands.addAll(args);

            final ProcessBuilder pB = new ProcessBuilder(commands);
            pB.inheritIO();
            pB.directory(config.outputDir.toFile());

            final String cmds = pB.command().stream().collect(Collectors.joining(" "));

            System.out.println(cmds);

            final Process process = pB.start();
            final int exitValue = process.waitFor();
            if (exitValue != 0)
            {
                System.out.println("Wrong exit Value: " + exitValue);
            }
            else
            {
                System.out.println("works!!");
            }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * @param config
     * @return
     */
    private static Optional<Path> findNativeImageExecutable(Config config)
    {

        Path exec = null;
        if (config.nativeImageExec != null)
        {
            exec = findNativeImageExec(Paths.get(config.nativeImageExec));
        }
        if (exec == null)
        {
            exec = findNativeImageExec(Paths.get("native-image"));
        }
        if (exec == null && System.getenv("GRAAL_HOME") != null)
        {
            exec = findNativeImageExec(Paths.get(System.getenv("GRAAL_HOME")));
        }

        if (exec == null && System.getProperty("java.home") != null)
        {
            exec = findNativeImageExec(Paths.get(System.getProperty("java.home")));
        }
        return Optional.ofNullable(exec);
    }

    /**
     * @param path
     */
    private static Path findNativeImageExec(Path path)
    {

        Path candidate = null;
        if (!Files.exists(path))
        {
            return candidate;
        }
        if (Files.isDirectory(path))
        {
            candidate = findNativeImageExec(path.resolve("native-image"));

            if (candidate == null)
            {
                candidate = findNativeImageExec(path.resolve("bin"));
            }

        }
        else //file o
        {

            try
            {
                final ProcessBuilder processBuilder = new ProcessBuilder(path.toString(),
                    "--version");

                final Process versionProcess = processBuilder.start();
                final Stream<String> lines = new BufferedReader(
                    new InputStreamReader(versionProcess.getInputStream())).lines();
                final Optional<String> versionLine = lines.filter(
                    l -> l.contains("GraalVM Version")).findFirst();

                if (!versionLine.isEmpty())
                {
                    System.out.println(versionLine.get());
                    candidate = path;
                }
            }
            catch (final IOException e)
            {
                e.printStackTrace();
            }

        }
        return candidate;

    }

    public static List<String> createArgs(Config config,
        Map<String, ReflectConfig> reflectConfigs,
        ResourceConfigResult resourceConfigResult) throws IOException
    {
        final List<String> args = new ArrayList<>();

        args.add("--allow-incomplete-classpath");

        //initialize-at-build-time

        final List<String> in = new ArrayList<>();
        if (resourceConfigResult.allResourcePackages != null)
        {
            in.addAll(resourceConfigResult.allResourcePackages);
        }
        if (config.additionalInitializeAtBuildTime != null)
        {
            in.addAll(config.additionalInitializeAtBuildTime);
        }

        final String initBuildTime = in.stream().sorted(
            (o1, o2) -> o1.compareTo(o2)).collect(Collectors.joining(","));

        if (initBuildTime != null && !initBuildTime.isEmpty())
        {
            args.add("--initialize-at-build-time=" + initBuildTime);
        }

        //H:ReflectionConfigurationFiles
        final String content = ReflectConfigUtil.createConfigContent(reflectConfigs);

        if (!content.isEmpty())
        {
            final Path reflectConfig = config.outputDir.resolve(
                "graal_reflect_config.json");
            Files.write(reflectConfig, content.getBytes());
        }
        if (config.reflectConfigFiles != null && !config.reflectConfigFiles.isEmpty())
        {
            final String reflCfgFiles = config.reflectConfigFiles.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(","));

            args.add("-H:ReflectionConfigurationFiles=" + reflCfgFiles);

        }
        //H:ResourceConfigurationFiles

        if (config.resourceConfigs != null && !config.resourceConfigs.isEmpty())
        {
            final String files = config.resourceConfigs.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(","));
            args.add("-H:ResourceConfigurationFiles=" + files);
        }

        //H:DynamicProxyConfigurationFiles
        if (config.dynamicProxyConfigurationFiles != null
            && !config.dynamicProxyConfigurationFiles.isEmpty())
        {
            args.add("-H:DynamicProxyConfigurationFiles="
                + config.dynamicProxyConfigurationFiles.stream().map(
                    p -> p.toAbsolutePath().toString()).collect(Collectors.joining(",")));
        }
        //other
        args.add("-H:+ReportUnsupportedElementsAtRuntime");
        args.add("-H:+ReportExceptionStackTraces");
        args.add("-H:+TraceClassInitialization");
        args.add("-H:+PrintClassInitialization");
        args.add("--no-fallback");
        if (config.debug)
        {
            args.add("--debug-attach");
        }
        args.add("-H:Class=" + config.mainClass);
        args.add("-H:Name=" + config.imageName);
        return args;

    }
}
