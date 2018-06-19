name := "redis-client"

version := "0.1"

scalaVersion := "2.12.6"

lazy val NETTY_VERSION = "4.1.25.Final"
lazy val SCALA_TEST = "3.0.5"

lazy val dependencies = Seq(
  "io.netty"       % "netty-all" % NETTY_VERSION,
  "org.scalatest" %% "scalatest" % SCALA_TEST    % Test
)

libraryDependencies ++= dependencies
