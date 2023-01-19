# jarloader
Function to load jars dynamically by java agents
You have to add the jarloader-<version>.jar file path to the JVM with the JVM command line parameter -javaagent:/path/to/jarloader-1.0.jar
Also add this file to the normal classpath of your application.
In your code you can load a jar file with:

```java
File jarFile = new File(pathToJar);
de.jlo.talendcomp.loadjaragent.JarLoader.addJarToClassPath(jarFile);
```
