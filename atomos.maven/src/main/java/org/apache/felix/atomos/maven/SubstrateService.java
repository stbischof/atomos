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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.osgi.framework.Constants;

public class SubstrateService
{

    /**
     *
     */
    private static final String ATOMOS_SUBSTRATE_JAR = "atomos.substrate.jar";
    private static final Collection<String> DEFAULT_EXCLUDE_NAMES = Arrays.asList(
        "about.html", "DEPENDENCIES", "LICENSE", "NOTICE", "changelog.txt",
        "LICENSE.txt");
    private static final Collection<String> DEFAULT_EXCLUDE_PATHS = Arrays.asList(
        "META-INF/maven/", "OSGI-OPT/");
    public static final String ATOMOS_BUNDLES = "/atomos/";
    public static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES + "bundles.index";
    private static final String ATOMOS_BUNDLE_SEPARATOR = "ATOMOS_BUNDLE";

    enum EntryType
    {
        PACKAGE, NON_PACKAGE, PACKAGE_CLASS, PACKAGE_RESOURCE, DEFAULT_PACKAGE_CLASS, DEFAULT_PACKAGE_RESOURCE, NON_PACKAGE_RESOURCE
    }

    private static boolean isClass(String path)
    {
        return path.endsWith(".class");
    }

    private static boolean filter(JarEntry entry, Config config)
    {
        final String path = entry.getName();
        if (entry.isDirectory() || isClass(path))
        {
            return false;
        }
        for (final String excludedPath : DEFAULT_EXCLUDE_PATHS)
        {
            if (path.startsWith(excludedPath))
            {
                return false;
            }
        }
        for (final String excludedName : DEFAULT_EXCLUDE_NAMES)
        {
            if (path.endsWith(excludedName))
            {
                return false;
            }
        }
        return true;
    }

    public static Path substrate(List<Path> files, Config config)
        throws IOException, NoSuchAlgorithmException
    {
        if (!config.outputDir.toFile().isDirectory())
        {
            throw new IllegalArgumentException(
                "Output file must be a directory." + config.outputDir);
        }
        if (!config.outputDir.toFile().exists())
        {
            Files.createDirectories(config.outputDir);
        }

        final Path p = config.outputDir.resolve(ATOMOS_SUBSTRATE_JAR);
        try (final ZipOutputStream z = new ZipOutputStream(
            new FileOutputStream(p.toFile()));)
        {

            final ZipEntry manifestZipEntry = new ZipEntry("/META-INF/MANIFEST.MF");
            z.putNextEntry(manifestZipEntry);
            z.write("Manifest-Version: 1.0".getBytes());
            final List<String> resources = new ArrayList<>();
            final AtomicLong counter = new AtomicLong(0);
            final Stream<SubstrateInfo> bis = files.stream()//
                .map(path -> create(z, counter.getAndIncrement(), path, config))//
                .peek(System.out::println);
            bis.forEach(s -> {
                resources.add(ATOMOS_BUNDLE_SEPARATOR);
                resources.add(s.id);
                resources.add(s.bsn);
                resources.add(s.version);
                s.files.forEach(resources::add);
            });
            writeBundleIndexFile(z, config.outputDir, resources);
        }
        System.out.println("end");
        return p;
    }

    private static void writeBundleIndexFile(ZipOutputStream z, Path output,
        final List<String> resources) throws IOException
    {

        final ZipEntry e = new ZipEntry(ATOMOS_BUNDLES_INDEX);
        z.putNextEntry(e);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out)))
        {
            resources.forEach((l) -> {
                try
                {
                    writer.append(l).append('\n');
                }
                catch (final IOException ex)
                {
                    throw new UncheckedIOException(ex);
                }
            });
        }
        z.write(out.toByteArray());

    }

    static SubstrateInfo create(ZipOutputStream z, long id, Path path, Config config)
    {
        final SubstrateInfo info = new SubstrateInfo();
        info.path = path;
        try (final JarFile jar = new JarFile(info.path.toFile()))
        {
            final Attributes attributes = jar.getManifest().getMainAttributes();
            info.bsn = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            info.version = attributes.getValue(Constants.BUNDLE_VERSION);
            info.id = Long.toString(id);

            info.files = jar.stream().filter(j -> filter(j, config)).peek(j -> {
                try
                {
                    final ZipEntry entry = new ZipEntry("/" + id + "/" + j.getName());
                    if (j.getCreationTime() != null)
                    {
                        entry.setCreationTime(j.getCreationTime());
                    }
                    if (j.getComment() != null)
                    {
                        entry.setComment(j.getComment());
                    }
                    z.putNextEntry(entry);
                    z.write(jar.getInputStream(j).readAllBytes());
                }
                catch (final IOException e)
                {
                    throw new UncheckedIOException(e);
                }

            }).peek(System.out::println).map(JarEntry::getName).collect(
                Collectors.toList());
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return info;
    }
}