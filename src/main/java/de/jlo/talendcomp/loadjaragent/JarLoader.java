/**
 * Copyright 2023 Jan Lolling jan.lolling@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jlo.talendcomp.loadjaragent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.JarFile;

/**
 * Loader of jar files at runtime. Use the official allowed and stable method of Instrumentation
 * Add to the java command line: -javaagent:/your/path/to/jarloader-1.0.jar
 * 
 * @author jan.lolling@gmail.com
 * Highly inspired by Chris Jennings
 *
 */
public class JarLoader {

	// here we keep the reference to Instrument to use it later
	// we get it from the Agent and use it in the application
	private static Instrumentation inst = null;
    private static ClassLoader systemClassLoader;
    private static Method addUrlMethod;
	
    /**
     * Adds a JAR file to the list of JAR files searched by the system class
     * loader. This effectively adds a new JAR to the class path.
     *
     * @param jarFile the JAR file to add
     * @throws Exception if there is an error accessing the JAR file
     */
    public static synchronized void addJarToClassPath(File jarFile) throws Exception {
        if (jarFile == null) {
            throw new IllegalArgumentException("jarFile cannot be null");
        }
        if (!jarFile.exists()) {
            throw new FileNotFoundException(jarFile.getAbsolutePath());
        }
        if (!jarFile.canRead()) {
            throw new IOException("Cannot read jar file: " + jarFile.getAbsolutePath());
        }
        if (jarFile.isDirectory()) {
            throw new IOException("Is a directory and not a jar file: " + jarFile.getAbsolutePath());
        }
        if (jarFile.getName().toLowerCase().endsWith(".jar") == false) {
        	throw new IllegalArgumentException("File: " + jarFile.getAbsolutePath() + " is not a jar file.");
        }
        // add the jar using instrumentation
        if (inst != null) {
        	JarFile j = new JarFile(jarFile);
            inst.appendToSystemClassLoaderSearch(j);
            return;
        }
        // Load via fall back reflection method. Will not work under Java9+
        if (getAddUrlMethod() != null) { // test if method was available
        	// if not this method throws a Exception
            try {
                getAddUrlMethod().invoke(systemClassLoader, jarFile.toURI().toURL());
            } catch (SecurityException iae) {
                throw new RuntimeException("security model prevents access to method", iae);
            } catch (Throwable t) {
            	throw new Exception("Load jar file: " + jarFile.getAbsolutePath() + " could not be loaded by reflection method.", t);
            }
        }
    }

	/**
	 * Called by the JRE. <em>Do not call this method from user code.</em>
	 *
	 * This method is called when the agent is attached to a running process. In
	 * practice, this is not how JarLoader is used, but it is implemented should you
	 * need it.
	 * 
	 * For this to work the {@code MANIFEST.MF} file must include
	 * the line {@code Agent-Class: de.jlo.talendcomp.loadjaragent.JarLoader}.
	 * 
	 * @param agentArgs       agent arguments; currently not used
	 * @param instrumentation provided by the Java Runtime
	 */
	public static void agentmain(String agentArgs, Instrumentation instrumentation) {
		if (instrumentation == null) {
			throw new IllegalArgumentException("instrumentation cannot be null");
		}
		if (inst == null) {
			// keep the Instrument instance for later use
			inst = instrumentation;
		}
	}

	/**
	 * Called by the JRE. <em>Do not call this method from user code.</em>
	 *
	 * This method is called when the agent is attached to a running process. In
	 * practice, this is not how JarLoader is used, but it is implemented should you
	 * need it.
	 * 
	 * For this to work the {@code MANIFEST.MF} file must include
	 * the line {@code Agent-Class: de.jlo.talendcomp.loadjaragent.JarLoader}.
	 * 
	 * @param agentArgs       agent arguments; currently not used
	 * @param instrumentation provided by the Java Runtime
	 */
	public static void premain(String agentArgs, Instrumentation instrumentation) {
		agentmain(agentArgs, instrumentation);
	}

	private static Method getAddUrlMethod() {
        if (addUrlMethod == null) {
            systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader instanceof URLClassLoader) {
                try {
                    final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    method.setAccessible(true);
                    addUrlMethod = method;
                } catch (NoSuchMethodException nsm) {
                    throw new IllegalStateException("The class " + URLClassLoader.class.getName() + " should have an addUrl() method, but its not!");
                }
            } else {
                throw new UnsupportedOperationException(
                        "Using fallback solution because Java agent instrumentation did not happend: SystemClassloader: " + systemClassLoader.getClass() + " is not an instance of URLClassLoader. This is typicall for Java9+. Please setup Java agent by using -javaagent /path/to/jarloader-1.0.jar"
                );
            }
        }
        return addUrlMethod;
    }

	public static String getLoadedClasses() {
		StringBuilder sb = new StringBuilder();
		if (inst != null) {
			Class<?>[] lc = inst.getAllLoadedClasses();
			Arrays.sort(lc, 0, lc.length, new Comparator<Class<?>>() {

				@Override
				public int compare(Class<?> c1, Class<?> c2) {
					return c1.getName().compareTo(c2.getName());
				}
				
			});
			for (Class<?> c : lc) {
				String name = c.getName();
				if (name.startsWith("java") || name.startsWith("com.sun") || name.startsWith("jdk") || name.startsWith("sun") || name.startsWith("org.omg") || name.startsWith("com.oracle") || name.startsWith("[")) {
					continue;
				}
				sb.append(name);
				sb.append("\n");
			}
		}
		return sb.toString();
	}

}