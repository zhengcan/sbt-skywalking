lazy val `skywalking-plugin` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.apache.skywalking",
    name := "sbt-skywalking",
    sbtPlugin := true,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayOrganization := Some("can"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.1"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5"),
  )

