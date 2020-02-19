/**
 *
 */
package org.apache.felix.atomos.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.ReflectConfig.ClassConfig;

public class BuildArgs
{

    public static List<String> create(Config config,
        Map<String, ClassConfig> reflectConfigs,
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
        final String content = ReflectConfig.createConfigContent(reflectConfigs);

        if (!content.isEmpty())
        {
            final Path reflectConfig = config.outputDir.resolve(
                "graal_reflect_config.json");
            Files.write(reflectConfig, content.getBytes());
            args.add("-H:ReflectionConfigurationFiles=" + reflectConfig.toString());
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
            args.add("--H:DynamicProxyConfigurationFiles="
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
