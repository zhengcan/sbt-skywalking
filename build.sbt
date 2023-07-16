lazy val `sbt-skywalking` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "io.github.zhengcan",
    name := "sbt-skywalking",
    sbtPlugin := true,
    scalaVersion := "2.12.15",
    crossSbtVersions := Seq("1.2.8", "1.3.13"),
    // javacOptions ++= Seq("-encoding", "UTF-8", "-source", "8", "-target", "8"),
    // scalacOptions ++= Seq("-encoding", "UTF-8", "-release", "8"),
    licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.7"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.6"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0"),
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % "1.21",
      "org.apache.logging.log4j" % "log4j-core" % "2.17.1" % Test
    )
  )

