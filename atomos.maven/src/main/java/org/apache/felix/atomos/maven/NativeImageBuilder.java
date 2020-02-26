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

public class NativeImageBuilder
{
    private static final String GRAAL_HOME = "GRAAL_HOME";
    private static final String JAVA_HOME = "java.home";
    private static final String NI_PARAMETER_ALLOW_INCOMPLETE_CLASSPATH = "--allow-incomplete-classpath";
    private static final String NI_PARAMETER_INITIALIZE_AT_BUILD_TIME = "--initialize-at-build-time=";
    private static final String NI_PARAMETER_H_REFLECTION_CONFIGURATION_FILES = "-H:ReflectionConfigurationFiles=";
    private static final String NI_PARAMETER_H_RESOURCE_CONFIGURATION_FILES = "-H:ResourceConfigurationFiles=";
    private static final String NI_PARAMETER_H_DYNAMIC_PROXY_CONFIGURATION_FILES = "-H:DynamicProxyConfigurationFiles=";
    private static final String NI_PARAMETER_NO_FALLBACK = "--no-fallback";
    private static final String NI_PARAMETER_DEBUG_ATTACH = "--debug-attach";
    private static final String NI_PARAMETER_H_REPORT_UNSUPPORTED_ELEMENTS_AT_RUNTIME = "-H:+ReportUnsupportedElementsAtRuntime";
    private static final String NI_PARAMETER_H_REPORT_EXCEPTION_STACK_TRACES = "-H:+ReportExceptionStackTraces";
    private static final String NI_PARAMETER_H_TRACE_CLASS_INITIALIZATION = "-H:+TraceClassInitialization";
    private static final String NI_PARAMETER_H_PRINT_CLASS_INITIALIZATION = "-H:+PrintClassInitialization";
    private static final String NI_PARAMETER_H_CLASS = "-H:Class=";
    private static final String NI_DEFAULT_NAME = "application";
    private static final String NI_PARAMETER_H_NAME = "-H:Name=";

    public static Path execute(Path nativeImageExecutable, Path outputDir,
        List<Path> classpath, List<String> args) throws Exception
    {
        final String cp = classpath.stream().map(
            p -> p.toAbsolutePath().toString()).collect(Collectors.joining(":"));

        final Optional<String> oName = args.stream().filter(
            s -> s.startsWith(NI_PARAMETER_H_NAME)).findFirst();

        Path resultExec = null;
        if (oName.isPresent())
        {
            final String name = oName.get().substring(NI_PARAMETER_H_NAME.length());
            resultExec = outputDir.resolve(name);

        }
        else
        {
            args.add(NI_PARAMETER_H_NAME + NI_DEFAULT_NAME);
            resultExec = outputDir.resolve(NI_DEFAULT_NAME);
        }

        final List<String> commands = new ArrayList<>();
        commands.add(nativeImageExecutable.toAbsolutePath().toString());
        commands.add("-cp");
        commands.add(cp);
        commands.addAll(args);

        final ProcessBuilder pB = new ProcessBuilder(commands);
        pB.inheritIO();
        pB.directory(outputDir.toFile());

        final boolean log = true;
        if (log)
        {
            final String cmds = pB.command().stream().collect(Collectors.joining(" "));
            System.out.println(cmds);
        }
        final Process process = pB.start();
        final int exitValue = process.waitFor();
        if (exitValue != 0)
        {
            throw new Exception("native-image returns exit value: " + exitValue);
        }
        if (Files.exists(resultExec))
        {
            return resultExec;
        }
        throw new Exception(
            "native-image could not be found: " + resultExec.toAbsolutePath().toString());
    }

    public static Optional<Path> findNativeImageExecutable(Path nativeImageExec)
    {
        Path exec = null;
        if (nativeImageExec != null)
        {
            exec = findNativeImageExec(nativeImageExec);
        }
        if (exec == null)
        {
            exec = findNativeImageExec(Paths.get("native-image"));
        }
        if (exec == null && System.getenv(GRAAL_HOME) != null)
        {
            exec = findNativeImageExec(Paths.get(System.getenv(GRAAL_HOME)));
        }
        if (exec == null && System.getProperty(JAVA_HOME) != null)
        {
            exec = findNativeImageExec(Paths.get(System.getProperty(JAVA_HOME)));
        }
        return Optional.ofNullable(exec);
    }

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
        args.add(NI_PARAMETER_ALLOW_INCOMPLETE_CLASSPATH);
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
            args.add(NI_PARAMETER_INITIALIZE_AT_BUILD_TIME + initBuildTime);
        }
        //H:ReflectionConfigurationFiles
        if (config.reflectConfigFiles != null && !config.reflectConfigFiles.isEmpty())
        {
            final String reflCfgFiles = config.reflectConfigFiles.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(","));
            args.add(NI_PARAMETER_H_REFLECTION_CONFIGURATION_FILES + reflCfgFiles);
        }
        //H:ResourceConfigurationFiles
        if (config.resourceConfigs != null && !config.resourceConfigs.isEmpty())
        {
            final String files = config.resourceConfigs.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(","));
            args.add(NI_PARAMETER_H_RESOURCE_CONFIGURATION_FILES + files);
        }
        //H:DynamicProxyConfigurationFiles
        if (config.dynamicProxyConfigurationFiles != null
            && !config.dynamicProxyConfigurationFiles.isEmpty())
        {
            args.add(NI_PARAMETER_H_DYNAMIC_PROXY_CONFIGURATION_FILES
                + config.dynamicProxyConfigurationFiles.stream().map(
                    p -> p.toAbsolutePath().toString()).collect(Collectors.joining(",")));
        }
        //other
        args.add(NI_PARAMETER_H_REPORT_UNSUPPORTED_ELEMENTS_AT_RUNTIME);
        args.add(NI_PARAMETER_H_REPORT_EXCEPTION_STACK_TRACES);
        args.add(NI_PARAMETER_H_TRACE_CLASS_INITIALIZATION);
        args.add(NI_PARAMETER_H_PRINT_CLASS_INITIALIZATION);
        args.add(NI_PARAMETER_NO_FALLBACK);
        if (config.debug)
        {
            args.add(NI_PARAMETER_DEBUG_ATTACH);
        }
        args.add(NI_PARAMETER_H_CLASS + config.mainClass);
        args.add(NI_PARAMETER_H_NAME + config.imageName);
        return args;

    }
}
