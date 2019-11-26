
lazy val `skywalking-plugin` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.apache.skywalking",
    name := "sbt-skywalking",
    sbtPlugin := true,
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.1"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5"),
  )

