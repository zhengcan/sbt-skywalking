package org.apache.skywalking.sbt

import sbt.Keys._
import sbt.{Compile, _}
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, ShadeRule, assemblyMergeStrategy, assemblyShadeRules}

object SkyWalkingAgentKeys {
}

object SkyWalkingAgent extends AutoPlugin {
  override def requires: Plugins = SkyWalkingBase && AssemblyPlugin

  val autoImport: SkyWalkingAgentKeys.type = SkyWalkingAgentKeys

  import SkyWalkingKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoScalaLibrary := false,
    crossPaths := false,
    sources in(Compile, doc) := Seq.empty,
    publishArtifact in(Compile, packageDoc) := false,

    libraryDependencies ++= Seq(
      "org.apache.skywalking" % "apm-agent-core" % skyWalkingVersion.value % Provided,
      "org.apache.skywalking" % "apm-util" % skyWalkingVersion.value % Provided,
      "org.apache.skywalking" % "apm-test-tools" % skyWalkingVersion.value % Test,
      "junit" % "junit" % "4.12" % Test,
      "org.mockito" % "mockito-all" % "1.10.19" % Test,
      "org.powermock" % "powermock-module-junit4" % "1.6.4" % Test,
      "org.powermock" % "powermock-api-mockito" % "1.6.4" % Test,
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("net.bytebuddy.**" -> "org.apache.skywalking.apm.dependencies.@0").inAll
    ),
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
}


