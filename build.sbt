crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.12.1"

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
lazy val scalaMock = "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test"
lazy val monix = "io.monix" %% "monix" % "2.2.2"
lazy val rxscala = "io.reactivex" %% "rxscala" % "0.26.5"
lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.22"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.8"
lazy val quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary
lazy val macroParadise = compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

lazy val `hyperbus-transport` = project in file("hyperbus-transport") settings (
    commonSettings,
    name := "hyperbus-transport",
    libraryDependencies ++= Seq(
      binders,
      jsonBinders,
      configBinders,
      monix,
      rxscala,
      slf4j,
      scalaMock
    )
  )

lazy val `hyperbus-model` = project in file("hyperbus-model") settings (
    commonSettings,
    name := "hyperbus-model",
    libraryDependencies ++= Seq(
      binders,
      slf4j,
      scalaMock
    )
  ) dependsOn `hyperbus-transport`

lazy val `hyperbus` = project in file("hyperbus") settings (
    commonSettings,
    name := "hyperbus",
    libraryDependencies ++= Seq(
      monix,
      rxscala,
      binders,
      scalaMock
    )
  ) dependsOn(`hyperbus-model`, `hyperbus-t-inproc`)

lazy val `hyperbus-t-inproc` = project in file("hyperbus-t-inproc") settings (
    commonSettings,
    name := "hyperbus-t-inproc",
    libraryDependencies ++= Seq(
      slf4j,
      scalaMock,
      logback % "test"
    )
  ) dependsOn `hyperbus-transport`

lazy val `hyperbus-root` = project.in(file(".")) aggregate (
    `hyperbus-transport`,
    `hyperbus-model`,
    `hyperbus`,
    `hyperbus-t-inproc`
  )