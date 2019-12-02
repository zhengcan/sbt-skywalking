
lazy val `agent-common` = (project in file("./agent-common"))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-lang3" % "3.9" % Provided,
      "net.jodah" % "failsafe" % "2.0.1"
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("net.jodah.**" -> "shaded.@0").inAll
    )
  )

lazy val `agent-web-only` = (project in file("./agent-web-only"))
  .enablePlugins(SkyWalkingAgent)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-text" % "1.6" % Provided
    )
  )

val apmSettings = Seq(
  skyWalkingVersion := "6.5.0",
  skyWalkingDownload := false,
  skyWalkingActivations := Seq(
    "org.apache.skywalking" % "apm-toolkit-logback-1.x-activation" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-toolkit-opentracing-activation" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-toolkit-trace-activation" % skyWalkingVersion.value
  ),
  skyWalkingPlugins := Seq(
    // Default
    "org.apache.skywalking" % "apm-jdbc-commons" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-jedis-2.x-plugin" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-mysql-5.x-plugin" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-mysql-commons" % skyWalkingVersion.value,
    // Optional
    "org.apache.skywalking" % "apm-gson-2.x-plugin" % skyWalkingVersion.value,
    "org.apache.skywalking" % "apm-play-2.x-plugin" % skyWalkingVersion.value
  ),
  skyWalkingPluginProjects := Seq(
    `agent-common`
  )
)

lazy val `service` = (project in file("./service"))
  .enablePlugins(PlayJava, SkyWalkingService)
  .settings(
    apmSettings,
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-lang3" % "3.9"
    ),
    skyWalkingPlugins ++= Seq(
      "org.apache.skywalking" % "apm-zookeeper-3.4.x-plugin" % skyWalkingVersion.value
    )
  )

lazy val `web` = (project in file("./web"))
  .enablePlugins(PlayJava, SkyWalkingService)
  .settings(
    apmSettings,
    skyWalkingPluginProjects ++= Seq(
      `agent-web-only`
    )
  )

lazy val `play-2x` = (project in file("."))
  .aggregate(`service`, `web`)


