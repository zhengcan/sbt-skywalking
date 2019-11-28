# sbt-skywalking

This sbt plugin adds [SkyWalking](https://skywalking.apache.org/) to projects.

## Dependencies

This plugin depends on `sbt-javaagent` and `sbt-native-packager` plugins.

## Install

Add this plugin to your `project/plugins.sbt`:
```scala
resolvers += Resolver.bintrayIvyRepo("can", "sbt-plugins")
addSbtPlugin("org.apache.skywalking" % "sbt-skywalking" % versionNumber)
```

Enable this plugins in `build.sbt`:
```scala
lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingAgent)
```

## Usage

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

### SkyWalking plugins

As default, the `agent/plugins` and `agent/optional-plugins` will be added.

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

### agent.conf

Projects should create a new file named `agent.conf` in `conf/skywalking`.
The `agent.conf` could be copied from SkyWalking distribution.

In fact, all files in `conf/skywalking` will be copied to `agent/config` folder in generated package.

### Stage / Dist

This plugin enhances `stage`, `dist` and `packageZipTarball` to install SkyWalking as java agent.



