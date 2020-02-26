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
package org.apache.felix.atomos.impl.runtime.substrate;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase;
import org.osgi.framework.connect.ConnectContent;

public class SubstrateIndexConnectContent implements ConnectContent
{
    static class URLConnectEntry implements ConnectEntry
    {
        private final String name;
        private final URL resource;

        URLConnectEntry(String name, URL resource)
        {
            this.name = name;
            this.resource = resource;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public long getContentLength()
        {
            try
            {
                return resource.openConnection().getContentLengthLong();
            }
            catch (final IOException e)
            {
                return -1;
            }
        }

        @Override
        public long getLastModified()
        {
            try
            {
                return resource.openConnection().getDate();
            }
            catch (final IOException e)
            {
                return 0;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return resource.openStream();
        }

    }

    final String index;
    final List<String> entries;

    SubstrateIndexConnectContent(String index, List<String> entries)
    {
        this.index = index;
        this.entries = Collections.unmodifiableList(entries);
    }

    @Override
    public Optional<Map<String, String>> getHeaders()
    {
        return Optional.empty();
    }

    @Override
    public Iterable<String> getEntries() throws IOException
    {
        return entries;
    }

    @Override
    public Optional<ConnectEntry> getEntry(String name)
    {
        if (entries.contains(name))
        {
            final URL resource = getClass().getResource(
                AtomosRuntimeBase.ATOMOS_BUNDLES + index + '/' + name);
            System.out.println("!" + resource);
            if (resource != null)
            {
                return Optional.of(new URLConnectEntry(name, resource));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ClassLoader> getClassLoader()
    {
        return Optional.of(new ProxyClassLoader(getClass().getClassLoader()));
    }

    @Override
    public void open() throws IOException
    {
        // do nothing
    }

    @Override
    public void close() throws IOException
    {
        // do nothing
    }

}
