/*
   Copyright 2014 Uproot Labs India Pvt Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package uno;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

public final class Uno extends ClassLoader {
  private final String[] unoPaths;
  private final ProtectionDomain[] domains;

  class UnoStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(final URL u) throws IOException {
      return new URLConnection(u) {

        @Override
        public InputStream getInputStream() throws IOException {
          // System.out.println("getting resource as stream	" + u.getPath());
          return AccessController.doPrivileged(new PrivilegedAction<InputStream>() {

            @Override
            public InputStream run() {
              return Uno.this.getResourceAsStream(u.getPath());
            }

          });
        }

        @Override
        public void connect() throws IOException {

        }
      };
    }

  }

  Uno(final String[] paths) {
    unoPaths = new String[paths.length];
    domains = new ProtectionDomain[paths.length];
    try {
      for (int i = 0; i < paths.length; i++) {
        unoPaths[i] = "/uno$$$" + paths[i] + "/";
        final URL url = new URL(null, "zipentry://uno/" + paths[i] + ".jar", new UnoStreamHandler());
        final CodeSource codeSource = new CodeSource(url, (Certificate[]) null);
        domains[i] = new ProtectionDomain(codeSource, null, this, null);
      }
    } catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] streamToBytes(final InputStream stream) throws IOException {
    // TODO: Optimisation: use a common array and builder, but will require //
    // synchronization
    int bytesRead = -1;
    final byte[] tempBuffer = new byte[1024];
    final ByteArrayOutputStream streamBuilder = new ByteArrayOutputStream();
    while ((bytesRead = stream.read(tempBuffer)) != -1) {
      streamBuilder.write(tempBuffer, 0, bytesRead);
    }
    return streamBuilder.toByteArray();
  }

  private void definePackage(final String className, final URL location) {

    final int i = className.lastIndexOf('.');
    if (i != -1) {
      final String pkgname = className.substring(0, i);
      // Check if package already loaded.
      final Package pkg = getPackage(pkgname);
      if (pkg == null) {
        definePackage(pkgname, null, null, null, null, null, null, location);
      }
    }
  }

  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    // System.out.println("Finding class: " + name);
    final String path = name.replaceAll("\\.", "/") + ".class";
    final Object[] result = getAsStream(path);
    final int pathIndex = (Integer) result[0];
    final InputStream stream = (InputStream) result[1];
    if (stream != null) {
      definePackage(name, domains[pathIndex].getCodeSource().getLocation());
      try {
        final byte[] bytes = streamToBytes(stream);
        // System.out.println(" defining class at: " + domains[pathIndex]);
        return defineClass(name, bytes, 0, bytes.length, domains[pathIndex]);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      throw new ClassNotFoundException(name);
    }

  }

  @Override
  public URL getResource(final String name) {
    // System.out.println("Getting resource as URL: " + name);

    return AccessController.doPrivileged(new PrivilegedAction<URL>() {

      public URL run() {

        URL url = null;
        int index = -1;
        for (int i = 0; i < unoPaths.length; i++) {
          final String resourcePath = unoPaths[i] + name;
          // System.out.println("  Trying resourcePath : " + resourcePath);
          url = getClass().getResource(resourcePath);
          if (url != null) {
            index = i;
            break;
          }
        }
        if (url == null) {
          // System.out.println("  resource not found : " + name);
          return null;
        } else {
          try { 
            final URL zipURL = new URL(null, "zipentry://uno/" + unoPaths[index] + name , new UnoStreamHandler());
            return zipURL;
          } catch (MalformedURLException e) {
            throw new RuntimeException(e);
          }
        }
      }
    });
  }

  @Override
  public InputStream getResourceAsStream(final String name) {
    // System.out.println("Finding resource: " + name);
    if (name.endsWith(".class")) {
      // TODO: check for permission if a security manager is installed.
      // if granted, then proceed as usual.
      return null;
    } else {
      final Object[] result = getAsStream(name);
      final InputStream stream = (InputStream) result[1];
      if (stream != null) {
        return stream;
      } else {
        return getSystemClassLoader().getResourceAsStream(name);
      }
    }
  }

  private Object[] getAsStream(final String path) {
    return AccessController.doPrivileged(new PrivilegedAction<Object[]>() {

      public Object[] run() {

        InputStream stream = null;
        int pathIndex = -1;
        if (path.startsWith("//uno$$$")) {
          String newPath = path.substring(1);
          stream = getClass().getResourceAsStream(newPath);
        } else {
          for (int i = 0; i < unoPaths.length; i++) {
            final String resourcePath = unoPaths[i] + path;
            // System.out.println("  Trying resourcePath : " + resourcePath);
            stream = getClass().getResourceAsStream(resourcePath);
            if (stream != null) {
              pathIndex = i;
              break;
            }
          }
        }
        if (stream == null) {
          // System.out.println("  resource not found : " + path);
        }
        return new Object[] { pathIndex, stream };
      }
    });
  }

  public void startMain(final String mainClassName, final String[] args) {
    AccessController.doPrivileged(new PrivilegedAction<Object>() {

      public Object run() {
        try {
          Thread.currentThread().setContextClassLoader(Uno.this);
          final Class<?> mainClass = loadClass(mainClassName);
          final Method mainMethod = mainClass.getMethod("main", new Class[] { String[].class });
          mainMethod.invoke(null, new Object[] { args });
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException e) {
          e.printStackTrace();
        }
        return null;
      }
    });

  }

  public static List<String> readUnoConfig(final String configResourceName) {
    final InputStream configStream = getSystemResourceAsStream(configResourceName);
    final BufferedReader reader = new BufferedReader(new InputStreamReader(configStream));
    String line = null;
    final List<String> lines = new ArrayList<>();
    try {
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("#")) {
          lines.add(line);
        }
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  public static void main(final String[] args) {
    final List<String> lines = readUnoConfig("unoConfig");
    final String[] paths = lines.subList(1, lines.size()).toArray(new String[0]);
    final Uno unoCL = new Uno(paths);
    final String mainClassName = lines.get(0);
    unoCL.startMain(mainClassName, args);
  }

}
