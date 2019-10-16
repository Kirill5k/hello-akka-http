name := "hello-akka-http"

version := "0.1"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.5.25"
lazy val akkaHttpVersion = "10.1.8"
lazy val scalaTestVersion = "3.0.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,

  "com.pauldijou" %% "jwt-spray-json" % "4.1.0",

  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.scalatest" % "scalatest_2.13" % scalaTestVersion % Test
)

