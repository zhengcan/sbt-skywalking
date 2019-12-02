// Play Framework
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")

// Sbt-SkyWalking
resolvers += Resolver.bintrayIvyRepo("can", "sbt-plugins")
addSbtPlugin("org.apache.skywalking" % "sbt-skywalking" % "0.0.5")
