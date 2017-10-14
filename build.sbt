crossScalaVersions := Seq("2.12.3", "2.11.11")

scalaVersion in Global := crossScalaVersions.value.head

lazy val commonSettings = Seq(
  version := "0.3-SNAPSHOT",
  organization := "com.hypertino",  
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public")
  ),
  libraryDependencies += {
    macroParadise
  }
)

// external dependencies
lazy val binders = "com.hypertino" %% "binders" % "1.2.0"
lazy val jsonBinders = "com.hypertino" %% "json-binders" % "1.2.0"
lazy val configBinders = "com.hypertino" %% "typesafe-config-binders" % "0.2.0"
lazy val ramlUtils = "com.hypertino" %% "hyperbus-utils" % "0.1-SNAPSHOT"
lazy val scalaMock = "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test"
lazy val monix = "io.monix" %% "monix" % "2.2.2"
lazy val scaldi = "org.scaldi" %% "scaldi" % "0.5.8"
lazy val scalaUri = "com.hypertino" %% "scala-uri" % "0.4.19-NO-SPRAY"
lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
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
      scalaLogging,
      scalaMock
    )
  ) dependsOn `hyperbus-macro`

lazy val `hyperbus-t-inproc` = project in file("hyperbus-t-inproc") settings (
    commonSettings,
    publishSettings,
    name := "hyperbus-t-inproc",
    libraryDependencies ++= Seq(
      scalaLogging,
      scalaMock,
      scaldi,
      logback % "test"
    )
  ) dependsOn `hyperbus`

lazy val `hyperbus-root` = project.in(file(".")) settings (
  publishSettings,
  publishArtifact := false,
  publishArtifact in Test := false,
  publish := {},
  publishLocal := {}
) aggregate (
    `hyperbus-macro`,
    `hyperbus`,
    `hyperbus-t-inproc`
  )

// Sonatype repositary publish options
val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },

  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false},

  // pgp keys and credentials
  pgpSecretRing := file("./travis/script/ht-oss-private.asc"),
  pgpPublicRing := file("./travis/script/ht-oss-public.asc"),
  usePgpKeyHex("F8CDEF49B0EDEDCC"),
  pgpPassphrase := Option(System.getenv().get("oss_gpg_passphrase")).map(_.toCharArray),

  pomExtra :=
    <url>https://github.com/hypertino/hyperbus</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:hypertino/hyperbus.git</url>
        <connection>scm:git:git@github.com:hypertino/hyperbus.git</connection>
      </scm>
      <developers>
        <developer>
          <id>maqdev</id>
          <name>Magomed Abdurakhmanov</name>
          <url>https://github.com/maqdev</url>
        </developer>
        <developer>
          <id>hypertino</id>
          <name>Hypertino</name>
          <url>https://github.com/hypertino</url>
        </developer>
      </developers>
  )

// Sonatype credentials
credentials ++= (for {
  username <- Option(System.getenv().get("sonatype_username"))
  password <- Option(System.getenv().get("sonatype_password"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
