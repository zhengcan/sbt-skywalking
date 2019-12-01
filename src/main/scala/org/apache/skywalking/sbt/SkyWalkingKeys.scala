package org.apache.skywalking.sbt

import sbt._

object SkyWalkingDefaults {
  val GROUP_ID = "org.apache.skywalking"
  val ARTIFACT_ID = "apm-agent"
  val VERSION = "6.5.0"
  val AGENT_NAME = "skywalking-agent"
  val MIRROR = "https://mirrors.cloud.tencent.com/apache/"
//  val MIRROR = "https://repo1.maven.org/maven2/org/apache/skywalking/apache-skywalking-apm/6.5.0/"
}

abstract class SkyWalkingKeys {
  val skyWalkingVersion = settingKey[String](s"The version of SkyWalking agent. (default ${SkyWalkingDefaults.VERSION})")
}

