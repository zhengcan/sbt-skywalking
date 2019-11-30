# sbt-skywalking

This project includes two kinds of sbt plugins:
- SkyWalkingService
    - adds [SkyWalking](https://skywalking.apache.org/) to projects.
- SkyWalkingAgent
    - develops a new SkyWalking agent project
    
## Install

Add this plugin to your `project/plugins.sbt`:
```scala
resolvers += Resolver.bintrayIvyRepo("can", "sbt-plugins")
addSbtPlugin("org.apache.skywalking" % "sbt-skywalking" % versionNumber)
```

This plugin depends on `sbt-javaagent`, `sbt-native-packager` and `sbt-assembly` plugins.

## Add SkyWalking agent

Enable `SkyWalkingServcie` plugin in `build.sbt`:
```scala
lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingServcie)
```

### Project Settings

This plugin add the following settings to project:
- `skyWalkingVersion`:
    - which SkyWalking will be used (default: `6.5.0`)
- `skyWalkingDirectory`:
    - where SkyWalking will be saved
- `skyWalkingMirror`:
    - which mirror of Apache download site will be used to download SkyWalking
- `skyWalkingPlugins`:
    - which plugins will be downloaded and added to `agent/plugins` folder
- `skyWalkingPluginProjects`:
    - which local projects will be added to `agent/plugins` folder as a SkyWalking plugin

### Add external SkyWalking agent

Developer could add extra plugin as below:
```scala
lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    skyWalkingPlugins ++= Seq(
      "group.id" % "artifact-id" % "version"
    )
  )
```

### Add local SkyWalking agent

If subproject should be added as a SkyWalking plugin, the build.sbt could be modified as below:
```scala
lazy val myPlugin = (project in file("./my-plugin"))

lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    skyWalkingPluginProjects ++= Seq(
      myPlugin
    )
  )
```

### Stage / Dist

This plugin enhances `stage`, `dist` and `packageZipTarball` to install SkyWalking as java agent.

As default, the `agent/plugins` and `agent/optional-plugins` will be added.

### agent.conf

Projects should create a new file named `agent.conf` in `conf/skywalking`.
The `agent.conf` could be copied from SkyWalking distribution.

In fact, all files in `conf/skywalking` will be copied to `agent/config` folder in generated package.

## Develop SkyWalking agent

Enable `SkyWalkingAgent` plugin in `build.sbt`:
```scala
lazy val myAgent = (project in file("./agent"))
  .enablePlugins(SkyWalkingAgent)
```

### Project Settings

This plugin add the following settings to project:
- `skyWalkingVersion`:
    - which SkyWalking will be used (default: `6.5.0`)




