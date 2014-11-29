UnoJar
=======

A single-jar packaging system. Compared to other solutions, this one is a little more complicated, but aims to be more performant.

`UnoJar` has a custom classloader that loads classes and resources from jars accompanying inside its own archive.
The jars are decompressed at build-time, improving performance at run-time.

For the end-user, it is still simple to run the application: `java -jar my-app.jar`.

### Building and deploying
Someday this will be automated. But for now, here's a description of the manual way.

* Create a directory called `app/`
* Explode the uno distribution into `app/`.
* Explode the application's jars and dependency jars into separated sub-directories under `app/`. The sub-directories
    should have a prefix: ```uno$$$``.
* Create a file called `app/unoConfig` which lists the jars and the entry point.

`TODO`: Add details about `unoConfig` syntax.

### Status
**This is a *proof-of-concept*. Being a class-loader there are major security implications. This code needs a lot 
of testing, reviews and audits.**

## Similar projects
* [OneJar](http://one-jar.sourceforge.net/)
* [JarClassLoader](http://www.jdotsoft.com/JarClassLoader.php)
* [100 line solution](http://qdolan.blogspot.in/2008/10/embedded-jar-classloader-in-under-100.html)

## Copyright and License
Copyright 2014 Uproot Labs India

Distributed under the [Apache v2 License](https://www.apache.org/licenses/LICENSE-2.0.html)
