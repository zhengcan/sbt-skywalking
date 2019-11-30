package org.apache.skywalking.sbt

import sbt._

object SkyWalkingDefaults {
  val GROUP_ID = "org.apache.skywalking"
  val ARTIFACT_ID = "skywalking-apm-agent"
  val VERSION = "6.5.0"
  val MIRROR = "https://mirrors.cloud.tencent.com/apache/"
}

abstract class SkyWalkingKeys {
  val skyWalkingVersion = settingKey[String](s"The version of SkyWalking agent. (default ${SkyWalkingDefaults.VERSION})")
}

