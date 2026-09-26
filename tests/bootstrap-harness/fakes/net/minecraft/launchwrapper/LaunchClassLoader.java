package net.minecraft.launchwrapper;

import java.net.URL;
import java.net.URLClassLoader;

/** Test double: real LaunchClassLoader also exposes a public addURL(URL). */
public class LaunchClassLoader extends URLClassLoader {
   public LaunchClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
   }

   @Override
   public void addURL(URL url) {
      super.addURL(url);
   }
}
