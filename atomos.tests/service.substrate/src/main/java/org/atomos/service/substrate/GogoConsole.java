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
package org.atomos.service.substrate;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.atomos.framework.AtomosRuntime;
import org.atomos.service.contract.Echo;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerContext;

public class GogoConsole
{
    public static void main(String[] args) throws BundleException, ClassNotFoundException
    {
        int exitCode = 0;
        try
        {

            long start = System.nanoTime();

            AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
            Map<String, String> config = AtomosRuntime.getConfiguration(args);
            config.putIfAbsent(LoggerContext.LOGGER_CONTEXT_DEFAULT_LOGLEVEL,
                LogLevel.AUDIT.name());
            Framework framework = atomosRuntime.newFramework(config);
            framework.init();
            BundleContext bc = framework.getBundleContext();
            LogReaderService logReader = bc.getService(
                bc.getServiceReference(LogReaderService.class));
            logReader.addLogListener((e) -> {
                System.out.println(getLogMessage(e));
            });
            framework.start();

            long total = System.nanoTime() - start;
            System.out.println("Total time: " + TimeUnit.NANOSECONDS.toMillis(total));

            Echo echo = bc.getService(bc.getServiceReference(Echo.class));
            String result = echo.echo("Echo works!");
            System.out.println(result);

            ServiceComponentRuntime scr = bc.getService(
                bc.getServiceReference(ServiceComponentRuntime.class));
            ComponentDescriptionDTO[] scrResult = scr.getComponentDescriptionDTOs().toArray(
                new ComponentDescriptionDTO[0]);
          for (ComponentDescriptionDTO componentDescriptionDTO : scrResult)
        {
            System.out.println(componentDescriptionDTO);
        }
        }
        catch (Exception e2)
        {
            exitCode = -1;
        }
        if (Arrays.asList(args).contains("-exit"))
        {
            System.exit(exitCode);
        }
    }

    private static String getLogMessage(LogEntry e)
    {
        StringBuilder builder = new StringBuilder(e.getMessage());
        if (e.getBundle() != null)
        {
            builder.append(" - bundle: " + e.getBundle());
        }
        if (e.getServiceReference() != null)
        {
            builder.append(" - service: " + e.getServiceReference());
        }
        return builder.toString();
    }
}
