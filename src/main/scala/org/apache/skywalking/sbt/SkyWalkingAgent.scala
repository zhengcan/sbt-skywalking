package org.apache.skywalking.sbt

import sbt.Keys._
import sbt.{Compile, _}
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._

object SkyWalkingAgentKeys {
}

object SkyWalkingAgent extends AutoPlugin {
  override def requires: Plugins = SkyWalkingBase && AssemblyPlugin

  val autoImport: SkyWalkingAgentKeys.type = SkyWalkingAgentKeys

  import SkyWalkingKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoScalaLibrary := false,
    crossPaths := false,
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    Compile / packageBin := (Compile / assembly).value,

    libraryDependencies ++= Seq(
      "org.apache.skywalking" % "apm-agent-core" % skyWalkingVersion.value % Provided,
//      "org.apache.skywalking" % "apm-util" % skyWalkingVersion.value % Provided,
      "org.apache.skywalking" % "apm-test-tools" % skyWalkingVersion.value % Test,
      "junit" % "junit" % "4.13.2" % Test,
      "org.mockito" % "mockito-all" % "1.10.19" % Test,
      "org.powermock" % "powermock-module-junit4" % "2.0.9" % Test,
      "org.powermock" % "powermock-api-mockito2" % "2.0.9" % Test,
      // Support junit in sbt
      "com.novocode" % "junit-interface" % "0.11" % Test
    ),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("net.bytebuddy.**" -> "org.apache.skywalking.apm.dependencies.@0").inAll
    ),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / test := {}
  )
}


