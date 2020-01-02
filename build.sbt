lazy val `skywalking-plugin` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.apache.skywalking",
    name := "sbt-skywalking",
    sbtPlugin := true,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayOrganization := Some("can2020"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.1"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10"),
  )

