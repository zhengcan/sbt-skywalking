# sbt-skywalking

[SkyWalking](http://skywalking.apache.org/) is an **A**pplication **P**erformance **M**onitor tool for distributed systems, especially designed for microservices, cloud native and container-based (Docker, K8s, Mesos) architectures.

The `sbt-skywalking` adds powerful features to easily use SkyWalking in sbt related project.



## Motivation

There are two kinds of SkyWalking related projects:

- applications to run SkyWalking as java agent to monitor performance
  - launch script should be modified to use SkyWalking as java agent
  - SkyWalking plugins should be manually added to plugins folder, or removed from it
  - SkyWalking agent config should be manually copied to agent folder
  - etc.
- SkyWalking agents to be installed as SkyWalking plugins
  - official agents are built by Maven, which is not so easy to be integrated with sbt



I want to remove all complicated or manual works, and empower the development of SkyWalking projects.



## Getting Started

The `sky-walking` should support the above two kinds of projects by provide two sbt plugins:

- `SkyWalkingService`
  - adds SkyWalking as java agent to current project after `stage`, `dist` or `packageZipTarball` 
- `SkyWalkingAgent`
  - makes current project to be a SkyWalking agent project 



### Install

Add this plugin as sbt dependency to your `project/plugins.sbt`:

```scala
# Currently this plugin is hosted in bintray 
resolvers += Resolver.bintrayIvyRepo("can2020", "sbt-plugins")

addSbtPlugin("org.apache.skywalking" % "sbt-skywalking" % versionNumber)
```

This plugin depends on `sbt-javaagent`, `sbt-native-packager` and `sbt-assembly` plugins.



## Use `SkyWalkingService` to add SkyWalking as java agent

First, we should enable `SkyWalkingServcie` plugin in `build.sbt`:

```scala
lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingServcie)
```



### Project settings

This plugin add the following settings to project:

- `skyWalkingVersion` : `String`
  - which SkyWalking will be used
  - default: `6.6.0`
- `skyWalkingEnableDefaultActivations` : `Boolean`
  - whether copy `activations/*.jar` from distribution to `agent/activations` folder
  - default: `true`
- `skyWalkingEnableDefaultPlugins` : `Boolean`
  - whether copy `plugins/*.jar` from distribution to `agent/plugins` folder
  - default: `true`
- `skyWalkingEnableOptionalPlugins` : `Boolean`
  - whether copy `optional-plugins/*.jar` from distribution to `agent/plugins`  folder
  - default: `false`
- `skyWalkingEnableBootstrapPlugins` : `Boolean`
  - whether copy `bootstrap-plugins/*.jar` from distribution to `agent/plugins`  folder
  - default: `false`
- `skyWalkingActivations` : `Seq[ModuleID]`
  - which activations will be downloaded and added to `agent/activations` folder
  - default: `Seq()`
- `skyWalkingPlugins` : `Seq[ModuleID]`
  - which plugins will be downloaded and added to `agent/plugins` folder
  - default: `Seq()`
- `skyWalkingPluginProjects` : `Seq[ProjectReference]`
  - which local projects will be added to `agent/plugins` folder as a SkyWalking agent plugin
  - default: `Seq()`
- `skyWalkingConfigDirectory` : `File`
  - where the `agent.conf` are stored
  - default: `[Project]/conf/skywalking`
- `skyWalkingDownload` : `Boolean`
  - whether download SkyWalking distribution if required
  - default: `true`
- `skyWalkingDownloadMirror` : `String`
  - which mirror of Apache download site will be used to download SkyWalking
  - default: `https://mirrors.cloud.tencent.com/apache/`
- `skyWalkingDownloadDirectory` : `File`
  - where SkyWalking will be saved
  - default: `[RootProject]/tools/skywalking-${skyWalkingVersion}`



### Download SkyWalking distribution

If the project doesn't include SkyWalking distribution, the plugin may download it from Apache mirror site, which is defined by `skyWalkingMirror`, automatically. And it will be unzipped to `skyWalkingDirectory`.

For example, in a multi-project sbt project, the folders may layouted as below.

```
ROOT/
  +-- agent/
    +-- src/
  +-- app/
    +-- src/
  +-- tools/
    +-- skywalking-6.6.0/       <== SkyWalking will be unzipped here
      +-- agent/
      +-- bin/
      +-- ...
```



Developer are freely to add / delete / modify the content if the SkyWalking folder, eg, remove unnecessary plugins. In fact, only `agent` folder in SkyWalking folder will be used in this plugin. Developer may delete all anything else.



### Not download SkyWalking distribution

If developer disabled download feature by using `skyWalkingDownload`, this plugin will try to download agent jar from maven central repository. But all plugins and activations muse be explicitly declared by using `skyWalkingPlugins` and `skyWalkingActivations`. 

By disable the download feature, no JARs are necessary to be pushed to scm.



### Decide which agent plugins should be used

Developer could use `skyWalkingEnableDefaultPlugins`, `skyWalkingEnableOptionalPlugins` and `skyWalkingEnableBootstrapPlugins` to control which agent plugins should be used. As default, only `agent/plugins/*.jar` will be used.



Besides the built-in agent plugins, developer also could add extra agent plugin as below: 

```scala
lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    skyWalkingPlugins ++= Seq(
      "group.id" % "artifact-id" % "version"
    )
  )
```

Due to all official agent plugins are published to maven central repository, it's really useful to avoid push these plugins to scm, and easily to control the agent plugin list by build script.



### Decide which activations should be used

Developer could use `skyWalkingEnableDefaultActivations` and `skyWalkingActivations`, which are equivalent to plugin related settings.



### Add local SkyWalking agent

If subproject should be added as a SkyWalking agent plugin, the build.sbt could be modified as below:

```scala
lazy val myAgent = (project in file("./agent"))
  .enablePlugins(SkyWalkingAgent)

lazy val myProject = (project in file("."))
  .enablePlugins(SkyWalkingService)
  .settings(
    skyWalkingPluginProjects ++= Seq(
      myAgent
    )
  )
```

This plugin enhanced the `compile`, `test`,  `clean` and other commands to aggregate the agent plugins declared in `skyWalkingPluginProjects`.



### agent.conf

Each `SkyWalkingService` enabled project should create a new file named `agent.conf` in `skyWalkingConfigDirectory`. Developer could copy the `agent.conf` could be copied from SkyWalking distribution.

In fact, all files in `skyWalkingConfigDirectory` will be copied to `agent/config` folder in generated package. And all sub-folders in `skyWalkingConfigDirectory` will be ignored.



### Generate project package

This plugin enhances `stage`, `dist` and `packageZipTarball`. By using `sbt-javaagent`, this plugin will add SkyWalking as java agent automatically, and copy plugins and agent.conf as required.



## Use `SkyWalkingAgent` to develop SkyWalking agent

First, we should enable `SkyWalkingAgent` plugin in `build.sbt`:
```scala
lazy val myAgent = (project in file("./agent"))
  .enablePlugins(SkyWalkingAgent)
```



### Project settings

This plugin add the following settings to project:
- `skyWalkingVersion` : `String`
  - which SkyWalking will be used
  - default: `6.6.0`



### The difference with official maven based agent

Generally, there are no meaningful difference between official maven based agent and sbt based agent. Developer may copy `src` folder from maven based agent project to sbt based agent project, and add customized library dependencies as required.



It means this plugin provide the same library dependencies as official maven script does.

```scala
# Add SkyWalking dependencies and test libraries
libraryDependencies ++= Seq(
  "org.apache.skywalking" % "apm-agent-core" % skyWalkingVersion.value % Provided,
  "org.apache.skywalking" % "apm-util" % skyWalkingVersion.value % Provided,
  "org.apache.skywalking" % "apm-test-tools" % skyWalkingVersion.value % Test,
  "junit" % "junit" % "4.12" % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test,
  "org.powermock" % "powermock-module-junit4" % "1.6.4" % Test,
  "org.powermock" % "powermock-api-mockito" % "1.6.4" % Test
)
```



And, this plugin use `sbt-assembly` to shade ByteBuddy related library as `maven-shade-plugin` does.

```scala
# Shade ByteBuddy related library
assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("net.bytebuddy.**" -> "org.apache.skywalking.apm.dependencies.@0").inAll
)
```



### Generate agent plugin package

Use `assembly` to build a shaded library as agent plugin.

If the agent plugin is referenced in `SkyWalkingService` enabled project,  it will be assembly automatically, and be copied to SkyWalking plugin folder.



### Develop agent for Play Framework

[Play Framework](https://www.playframework.com/) will enable [Play Enhancer](https://github.com/playframework/play-enhancer/) automatically, which may add some issues in SkyWalking agent.

Please consider to disable Play Enhancer as follow:

```scala
lazy val myAgent = (project in file("./agent"))
  .enablePlugins(SkyWalkingAgent)
  .disablePlugins(PlayEnhancer)
```



## Examples

Here are some examples.
Developer may check `examples` folder for complex demo.

### Multi-project with SkyWalking agent

```scala
lazy val `myAgent` = (project in file("./agent"))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    libraryDependencies ++= Seq(
      "other.project" % "not-assemblied-with-agent" % "x.y.z" % Provided,
      "other.project" % "will-assemblied-with-agent" % "x.y.z"
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("other.project.should.be.shaded.**" -> "shaded.@0").inAll
    )
  )

lazy val `myApp` = (project in file("./app"))
  .enablePlugins(SkyWalkingService)
  .settings(
    skyWalkingPlugins ++= Seq(
      "org.apache.skywalking" % "apm-zookeeper-3.4.x-plugin" % "6.6.0"
    ),
    skyWalkingPluginProjects ++= Seq(
      `myAgent`
    )
  )
```



### Not download SkyWalking distribution

```scala
lazy val `myApp` = (project in file("./app"))
  .enablePlugins(SkyWalkingService)
  .settings(
    skyWalkingDownload := false,
    skyWalkingActivations ++= Seq(
      "org.apache.skywalking" % "apm-toolkit-logback-1.x-activation" % "6.6.0",
      "org.apache.skywalking" % "apm-toolkit-opentracing-activation" % "6.6.0",
      "org.apache.skywalking" % "apm-toolkit-trace-activation" % "6.6.0"
    ),
    skyWalkingPlugins ++= Seq(
      "org.apache.skywalking" % "apm-jedis-2.x-plugin" % "6.6.0",
      "org.apache.skywalking" % "apm-mysql-8.x-plugin" % "6.6.0",
      "org.apache.skywalking" % "apm-mysql-commons" % "6.6.0"
    )
  )
```



