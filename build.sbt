lazy val `skywalking-plugin` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.apache.skywalking",
    name := "sbt-skywalking",
    sbtPlugin := true,
    licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.7"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.6"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0"),
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % "1.21"
    )
  )

