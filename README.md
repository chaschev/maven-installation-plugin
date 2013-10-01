Welcome
=======

Installation is a plugin which installs and executes your plugins. It is similar to Unix package managers installations like `yum install <package>`, `apt-get install <package>` or `gem install <package>` in a Ruby language SDK.

It's built for Maven, so it uses a bit different syntax:

```bash
$ mvn installation:install -Dartifact=groupId:artifactId
```
    
And this will install your latest artifact version to the system.

Quick Start
-----------

Add these lines to your settings.xml (`$HOME/.m2/settings.xml`):

```xml
<settings>
  ...
  <pluginGroups>
    <pluginGroup>com.chaschev</pluginGroup>
  </pluginGroups>
  ...
</settings>
```

To install a dummy command to your system, in console run

    $ mvn installation:install -Dartifact=com.chaschev:chutils
    
Now, command `chutils` from artifact `com.chaschev:chutils:1.1` should be available on your command line with all of it's dependencies. To check this run

    $ chutils Austin Day
    
and this should print you a greeting.

Features
--------

- Windows and Unix are supported.
- Shortcuts are installed to your Maven bin dir or when on Unix to your local bin-dirs.

### How it works

Plugin fetches an artifact with all dependences to your local repository. It also creates shortcuts which link to a `Runner` jar which launches your apps. This transition was made to support long classpaths on Windows.

Requirements
------------

- JDK 6
- Maven (todo check Maven versions)
 
How to Use
----------

### Configuring Maven (for end-users and developers)

Add these lines to your settings.xml (`$HOME/.m2/settings.xml`):

```xml
<settings>
  ...
  <pluginGroups>
    <pluginGroup>com.chaschev</pluginGroup>
  </pluginGroups>
  ...
</settings>
```

### Making a Maven Artifact installable (for developers)

Create an `Installation.java` class in your root package with similar content:

```java
public class Installation {
    public static final List<Object[]> shortcuts =
        Collections.singletonList(
            new Object[]{"chutils", Main.class}
        );
}
```

This is a list of shortcuts to your main classes the plugin will create.

Deploy your artifact to your repository. Local and remote non-central maven repositories are supported. I.e.

    $ cd your-project-dir
    $ mvn install
    
Install your artifact to the local repository

    $ mvn installation:install -Dartifact=groupId.artifactId
    
Next try running your shortcuts. If there are no exceptions, the installation was ok.

Contributing
============

Contributions are welcome! Tips below might be useful for testing and extending the Installation plugin:

Running from remote repository:

     $ mvn -U com.chaschev:installation-maven-plugin:1.X-SNAPSHOT:install -Dartifact=com.chaschev:chutils
     
This will force an update (`-U`) and will explicitly specify the Installation plugin version.

Adding plugin repositories in `settings.xml`, i.e.:

```xml
<settings>
  ...
  <profiles>
    <profile>
      <id>allow-snapshots</id>
      <activation><activeByDefault>true</activeByDefault></activation>
      <repositories>
        <repository>
          <id>snapshots-repo</id>
          <url>https://oss.sonatype.org/content/repositories/snapshots</url>
          <releases><enabled>false</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    <pluginRepositories>
      <pluginRepository>
        <id>snapshots-plugin-repo</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
      </pluginRepository>
    </pluginRepositories>
    </profile>
  </profiles>
  </activeProfiles>
    <activeProfile>allow-snapshots</activeProfile>
  </activeProfiles>
  ...
</settings>
```

Credits
=======

Thanks go to [yegor256](https://github.com/yegor256) for his contributions to Maven Aether.
