package org.apache.skywalking.sbt

import sbt._

object SkyWalkingDefaults {
  val GROUP_ID = "org.apache.skywalking"
  val ARTIFACT_ID = "apm-agent"
  val VERSION = "8.8.0"
  val AGENT_NAME = "skywalking-agent"
  val MIRROR = "https://dlcdn.apache.org/skywalking/java-agent/"
}

object SkyWalkingKeys {
  val skyWalkingVersion = settingKey[String](s"The version of SkyWalking agent. (default ${SkyWalkingDefaults.VERSION})")
}

object SkyWalkingBase extends AutoPlugin {
  val autoImport: SkyWalkingKeys.type = SkyWalkingKeys

  import SkyWalkingKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    skyWalkingVersion := SkyWalkingDefaults.VERSION,
  )
}


