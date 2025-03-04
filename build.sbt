import microsites.ExtraMdFileConfig
import com.typesafe.tools.mima.core._
import sbtrelease.Version
import sbtcrossproject.crossProject

val ReleaseTag = """^release/([\d\.]+a?)$""".r

addCommandAlias("fmt", "; compile:scalafmt; test:scalafmt; scalafmtSbt")
addCommandAlias("fmtCheck", "; compile:scalafmtCheck; test:scalafmtCheck; scalafmtSbtCheck")

lazy val contributors = Seq(
  "pchiusano" -> "Paul Chiusano",
  "pchlupacek" -> "Pavel Chlupáček",
  "SystemFw" -> "Fabio Labella",
  "alissapajer" -> "Alissa Pajer",
  "djspiewak" -> "Daniel Spiewak",
  "fthomas" -> "Frank Thomas",
  "runarorama" -> "Rúnar Ó. Bjarnason",
  "jedws" -> "Jed Wesley-Smith",
  "mpilquist" -> "Michael Pilquist",
  "durban" -> "Daniel Urban"
)

lazy val commonSettings = Seq(
  organization := "co.fs2",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:implicitConversions",
    "-language:higherKinds"
  ) ++
    (scalaBinaryVersion.value match {
      case v if v.startsWith("2.13") =>
        List("-Xlint", "-Ywarn-unused")
      case v if v.startsWith("2.12") =>
        List("-Ypartial-unification")
      case v if v.startsWith("2.11") =>
        List("-Xexperimental", "-Ypartial-unification")
      case other => sys.error(s"Unsupported scala version: $other")
    }),
  scalacOptions in (Compile, console) ~= {
    _.filterNot("-Ywarn-unused" == _)
      .filterNot("-Xlint" == _)
      .filterNot("-Xfatal-warnings" == _)
  },
  // Disable fatal warnings for test compilation because sbt-doctest generated tests
  // generate warnings which lead to test failures.
  scalacOptions in (Test, compile) ~= {
    _.filterNot("-Xfatal-warnings" == _)
  },
  scalacOptions in (Compile, console) += "-Ydelambdafy:inline",
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  javaOptions in (Test, run) ++= Seq("-Xms64m", "-Xmx64m"),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    "org.typelevel" %%% "cats-core" % "2.0.0",
    "org.typelevel" %%% "cats-laws" % "2.0.0" % "test",
    "org.typelevel" %%% "cats-effect" % "2.0.0",
    "org.typelevel" %%% "cats-effect-laws" % "2.0.0" % "test",
    "org.scalacheck" %%% "scalacheck" % "1.14.2" % "test",
    "org.scalatest" %%% "scalatest" % "3.1.0-SNAP13" % "test",
    "org.scalatestplus" %%% "scalatestplus-scalacheck" % "1.0.0-M2" % "test"
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/functional-streams-for-scala/fs2"),
      "git@github.com:functional-streams-for-scala/fs2.git"
    )
  ),
  homepage := Some(url("https://github.com/functional-streams-for-scala/fs2")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  initialCommands := s"""
    import fs2._, cats.effect._, cats.effect.implicits._, cats.implicits._
    import scala.concurrent.ExecutionContext.Implicits.global, scala.concurrent.duration._
    implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)
    implicit val timerIO: Timer[IO] = IO.timer(global)
  """,
  doctestTestFramework := DoctestTestFramework.ScalaTest
) ++ testSettings ++ scaladocSettings ++ publishingSettings ++ releaseSettings

lazy val testSettings = Seq(
  fork in Test := !isScalaJSProject.value,
  javaOptions in Test ++= (Seq(
    "-Dscala.concurrent.context.minThreads=8",
    "-Dscala.concurrent.context.numThreads=8",
    "-Dscala.concurrent.context.maxThreads=8"
  ) ++ (sys.props.get("fs2.test.travis") match {
    case Some(value) =>
      Seq(s"-Dfs2.test.travis=true")
    case None => Seq()
  })),
  parallelExecution in Test := false,
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  publishArtifact in Test := true
)

lazy val tutSettings = Seq(
  scalacOptions in Tut ~= {
    _.filterNot("-Ywarn-unused-import" == _)
      .filterNot("-Ywarn-unused" == _)
      .filterNot("-Xlint" == _)
      .filterNot("-Xfatal-warnings" == _)
  },
  scalacOptions in Tut += "-Ydelambdafy:inline"
)

def scmBranch(v: String): String = {
  val Some(ver) = Version(v)
  if (ver.qualifier.exists(_ == "-SNAPSHOT"))
    // support branch (0.9.0-SNAPSHOT -> series/0.9)
    s"series/${ver.copy(subversions = ver.subversions.take(1), qualifier = None).string}"
  else
    // release tag (0.9.0-M2 -> v0.9.0-M2)
    s"v${ver.string}"
}

lazy val scaladocSettings = Seq(
  scalacOptions in (Compile, doc) ++= Seq(
    "-doc-source-url",
    s"${scmInfo.value.get.browseUrl}/tree/${scmBranch(version.value)}€{FILE_PATH}.scala",
    "-sourcepath",
    baseDirectory.in(LocalRootProject).value.getAbsolutePath,
    "-implicits",
    "-implicits-sound-shadowing",
    "-implicits-show-all"
  ),
  scalacOptions in (Compile, doc) ~= { _.filterNot { _ == "-Xfatal-warnings" } },
  autoAPIMappings := true
)

lazy val publishingSettings = Seq(
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots".at(nexus + "content/repositories/snapshots"))
    else
      Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
  },
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq,
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  pomExtra := {
    <developers>
      {for ((username, name) <- contributors) yield <developer>
        <id>{username}</id>
        <name>{name}</name>
        <url>http://github.com/{username}</url>
      </developer>}
    </developers>
  },
  pomPostProcess := { node =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) = new RewriteRule {
      override def transform(n: Node) =
        if (f(n)) NodeSeq.Empty else n
    }
    val stripTestScope = stripIf { n =>
      n.label == "dependency" && (n \ "scope").text == "test"
    }
    new RuleTransformer(stripTestScope).transform(node)(0)
  },
  gpgWarnOnFailure := Option(System.getenv().get("GPG_WARN_ON_FAILURE")).isDefined
)

lazy val commonJsSettings = Seq(
  scalaJSOptimizerOptions ~= { options =>
    // https://github.com/scala-js/scala-js/issues/2798
    try {
      scala.util.Properties.isJavaAtLeast("1.8")
      options
    } catch {
      case _: NumberFormatException =>
        options.withParallel(false)
    }
  },
  scalaJSStage in Test := FastOptStage,
  jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
  scalacOptions in Compile += {
    val dir = project.base.toURI.toString.replaceFirst("[^/]+/?$", "")
    val url =
      "https://raw.githubusercontent.com/functional-streams-for-scala/fs2"
    s"-P:scalajs:mapSourceURI:$dir->$url/${scmBranch(version.value)}/"
  }
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val releaseSettings = Seq(
  releaseCrossBuild := true
)

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := {
    List("2.0.0").map { pv =>
      organization.value % (normalizedName.value + "_" + scalaBinaryVersion.value) % pv
    }.toSet
  },
  mimaBinaryIssueFilters ++= Seq(
    // No bincompat on internal package
    ProblemFilters.exclude[Problem]("fs2.internal.*"),
    // Mima reports all ScalaSignature changes as errors, despite the fact that they don't cause bincompat issues when version swapping (see https://github.com/lightbend/mima/issues/361)
    ProblemFilters.exclude[IncompatibleSignatureProblem]("*")
  )
)

lazy val root = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(noPublish)
  .aggregate(coreJVM, coreJS, io, reactiveStreams, benchmark, experimental)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "fs2-core",
    sourceDirectories in (Compile, scalafmt) += baseDirectory.value / "../shared/src/main/scala",
    unmanagedSourceDirectories in Compile += {
      val dir = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v >= 13 => "scala-2.13+"
        case _                       => "scala-2.12-"
      }
      baseDirectory.value / "../shared/src/main" / dir
    },
    libraryDependencies += "org.scodec" %%% "scodec-bits" % "1.1.12"
  )
  .jsSettings(commonJsSettings: _*)

lazy val coreJVM = core.jvm
  .enablePlugins(SbtOsgi)
  .settings(
    OsgiKeys.exportPackage := Seq("fs2.*"),
    OsgiKeys.privatePackage := Seq(),
    OsgiKeys.importPackage := {
      val Some((major, minor)) = CrossVersion.partialVersion(scalaVersion.value)
      Seq(s"""scala.*;version="[$major.$minor,$major.${minor + 1})"""", "*")
    },
    OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package"),
    osgiSettings
  )
  .settings(mimaSettings)

lazy val coreJS = core.js.disablePlugins(DoctestPlugin, MimaPlugin)

lazy val io = project
  .in(file("io"))
  .enablePlugins(SbtOsgi)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    name := "fs2-io",
    OsgiKeys.exportPackage := Seq("fs2.io.*"),
    OsgiKeys.privatePackage := Seq(),
    OsgiKeys.importPackage := {
      val Some((major, minor)) = CrossVersion.partialVersion(scalaVersion.value)
      Seq(
        s"""scala.*;version="[$major.$minor,$major.${minor + 1})"""",
        """fs2.*;version="${Bundle-Version}"""",
        "*"
      )
    },
    OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package"),
    osgiSettings
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

lazy val reactiveStreams = project
  .in(file("reactive-streams"))
  .enablePlugins(SbtOsgi)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivestreams" % "reactive-streams" % "1.0.3",
      "org.reactivestreams" % "reactive-streams-tck" % "1.0.3" % "test",
      "org.scalatestplus" %% "scalatestplus-testng" % "1.0.0-M2" % "test"
    )
  )
  .settings(mimaSettings)
  .settings(
    name := "fs2-reactive-streams",
    OsgiKeys.exportPackage := Seq("fs2.interop.reactivestreams.*"),
    OsgiKeys.privatePackage := Seq(),
    OsgiKeys.importPackage := {
      val Some((major, minor)) = CrossVersion.partialVersion(scalaVersion.value)
      Seq(
        s"""scala.*;version="[$major.$minor,$major.${minor + 1})"""",
        """fs2.*;version="${Bundle-Version}"""",
        "*"
      )
    },
    OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package"),
    osgiSettings
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

lazy val benchmarkMacros = project
  .in(file("benchmark-macros"))
  .disablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(noPublish)
  .settings(
    name := "fs2-benchmark-macros",
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v >= 13 =>
          List("-Ymacro-annotations")
        case _ =>
          Nil
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v <= 12 =>
          Seq(
            compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
          )
        case _ =>
          Nil
      }
    },
    libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value
  )

lazy val benchmark = project
  .in(file("benchmark"))
  .disablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(noPublish)
  .settings(
    name := "fs2-benchmark",
    javaOptions in (Test, run) := (javaOptions in (Test, run)).value
      .filterNot(o => o.startsWith("-Xmx") || o.startsWith("-Xms")) ++ Seq("-Xms256m", "-Xmx256m"),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v >= 13 =>
          List("-Ymacro-annotations")
        case _ =>
          Nil
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v <= 12 =>
          Seq(
            compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
          )
        case _ =>
          Nil
      }
    }
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(io, benchmarkMacros)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(TutPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-docs",
    tutSourceDirectory := file("docs") / "src",
    tutTargetDirectory := file("docs")
  )
  .settings(tutSettings)
  .dependsOn(coreJVM, io, reactiveStreams)

lazy val microsite = project
  .in(file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings)
  .settings(
    micrositeName := "fs2",
    micrositeDescription := "Purely functional, effectful, resource-safe, concurrent streams for Scala",
    micrositeGithubOwner := "functional-streams-for-scala",
    micrositeGithubRepo := "fs2",
    micrositeBaseUrl := "",
    micrositeExtraMdFiles := Map(
      file("README.md") -> ExtraMdFileConfig(
        "index.md",
        "home",
        Map("title" -> "Home", "section" -> "home", "position" -> "0")
      )
    )
  )
  .settings(tutSettings)
  .dependsOn(coreJVM, io, reactiveStreams)

lazy val experimental = project
  .in(file("experimental"))
  .enablePlugins(SbtOsgi)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    name := "fs2-experimental",
    OsgiKeys.exportPackage := Seq("fs2.experimental.*"),
    OsgiKeys.privatePackage := Seq(),
    OsgiKeys.importPackage := {
      val Some((major, minor)) = CrossVersion.partialVersion(scalaVersion.value)
      Seq(
        s"""scala.*;version="[$major.$minor,$major.${minor + 1})"""",
        """fs2.*;version="${Bundle-Version}"""",
        "*"
      )
    },
    OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package"),
    osgiSettings
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

addCommandAlias("testJVM", ";coreJVM/test;io/test;reactiveStreams/test;benchmark/test")
addCommandAlias("testJS", "coreJS/test")
