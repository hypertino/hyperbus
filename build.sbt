crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.11.8"

lazy val commonSettings = Seq(
  version := "0.2-SNAPSHOT",
  organization := "com.hypertino",  
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public")
  ),
  libraryDependencies += {
    macroParadise
  }
)

// external dependencies
lazy val binders = "com.hypertino" %% "binders" % "1.0-SNAPSHOT"
lazy val jsonBinders = "com.hypertino" %% "json-binders" % "1.0-SNAPSHOT"
lazy val configBinders = "com.hypertino" %% "typesafe-config-binders" % "0.13-SNAPSHOT"
lazy val ramlUtils = "com.hypertino" %% "hyperbus-utils" % "0.1-SNAPSHOT"
lazy val scalaMock = "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test"
lazy val monix = "io.monix" %% "monix" % "2.2.2"
lazy val scaldi = "org.scaldi" %% "scaldi" % "0.5.8"
lazy val scalaUri = "com.hypertino" %% "scala-uri" % "0.4.17-NO-SPRAY"
lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.22"
lazy val apacheLang3 = "org.apache.commons" % "commons-lang3" % "3.6"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.8"
lazy val quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary
lazy val macroParadise = compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

lazy val `hyperbus-macro` = project in file("hyperbus-macro") settings (
  commonSettings,
  publishSettings,
  name := "hyperbus-macro",
  libraryDependencies ++= Seq(
    binders
  )
)

lazy val `hyperbus` = project in file("hyperbus") settings (
    commonSettings,
    publishSettings,
    name := "hyperbus",
    libraryDependencies ++= Seq(
      monix,
      binders,
      jsonBinders,
      ramlUtils,
      scaldi,
      configBinders,
      scalaUri,
      apacheLang3,
      slf4j,
      scalaMock
    )
  ) dependsOn `hyperbus-macro`

lazy val `hyperbus-t-inproc` = project in file("hyperbus-t-inproc") settings (
    commonSettings,
    publishSettings,
    name := "hyperbus-t-inproc",
    libraryDependencies ++= Seq(
      slf4j,
      scalaMock,
      scaldi,
      logback % "test"
    )
  ) dependsOn `hyperbus`

lazy val `hyperbus-root` = project.in(file(".")) settings (
    publishArtifact := false
  ) aggregate (
    `hyperbus-macro`,
    `hyperbus`,
    `hyperbus-t-inproc`
  )