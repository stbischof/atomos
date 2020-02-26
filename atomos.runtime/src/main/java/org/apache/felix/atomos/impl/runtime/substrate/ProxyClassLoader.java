/**
 * 
 */
package org.apache.felix.atomos.impl.runtime.substrate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.stream.Stream;

/**
 * @author stbischof
 *
 */
public class ProxyClassLoader extends ClassLoader
{
    /**
     * 
     */
    public ProxyClassLoader(ClassLoader classLoader)
    {
        this.classLoader = classLoader;
    }

    ClassLoader classLoader;

    public int hashCode()
    {
        return classLoader.hashCode();
    }

    public boolean equals(Object obj)
    {
        return classLoader.equals(obj);
    }

    public String toString()
    {
        return classLoader.toString();
    }

    public String getName()
    {
        return classLoader.getName();
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        return classLoader.loadClass(name);
    }

    public URL getResource(String name)
    {

        System.out.println("< " + name);
        return classLoader.getResource(name);
    }

    public Enumeration<URL> getResources(String name) throws IOException
    {

        System.out.println("# " + name);
        return classLoader.getResources(name);
    }

    public Stream<URL> resources(String name)
    {

        System.out.println(", " + name);
        return classLoader.resources(name);
    }

    public InputStream getResourceAsStream(String name)
    {

        System.out.println(". " + name);
        return classLoader.getResourceAsStream(name);
    }

    public void setDefaultAssertionStatus(boolean enabled)
    {
        classLoader.setDefaultAssertionStatus(enabled);
    }

    public void setPackageAssertionStatus(String packageName, boolean enabled)
    {
        classLoader.setPackageAssertionStatus(packageName, enabled);
    }

    public void setClassAssertionStatus(String className, boolean enabled)
    {
        classLoader.setClassAssertionStatus(className, enabled);
    }

    public void clearAssertionStatus()
    {
        classLoader.clearAssertionStatus();
    }

}
