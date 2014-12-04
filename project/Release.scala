import sbt._
import sbt.Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.pgp.PgpKeys

object Release {
  lazy val settings = releaseSettings ++ Seq(
    ReleaseKeys.crossBuild := true,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
    ReleaseKeys.releaseVersion := selectReleaseVersion.value,
    ReleaseKeys.nextVersion := selectNextVersion.value,
    ReleaseKeys.releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      runTestIn("docs"),
      runTestIn("samples"),
      setBuildReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setBuildNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

  def runTestIn(projectName: String) = ReleaseStep(
    action = { state: State =>
      if (!state.get(ReleaseKeys.skipTests).getOrElse(false)) {
        val extracted = Project.extract(state)
        val ref = LocalProject(projectName)
        extracted.runAggregated(test in Test in ref, state)
      } else state
    },
    enableCrossBuild = true
  )

  // use any non-snapshot version directly
  def selectReleaseVersion = Def.setting {
    val default = ReleaseKeys.releaseVersion.value
    (v: String) => { if (v endsWith "-SNAPSHOT") default(v) else v }
  }

  // don't bump the version for qualified releases, like milestones and release candidates
  def selectNextVersion = Def.setting {
    val default = ReleaseKeys.nextVersion.value
    (v: String) => { sbtrelease.Version(v).filter(_.qualifier.isDefined).fold(default(v))(_.asSnapshot.string) }
  }

  lazy val setBuildReleaseVersion: ReleaseStep = setBuildVersion(_._1)
  lazy val setBuildNextVersion: ReleaseStep = setBuildVersion(_._2)

  def setBuildVersion(selectVersion: Versions => String): ReleaseStep = { state: State =>
    val versions = state.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(versions)
    state.log.info(s"Building against play ${Version.play}")
    state.log.info(s"Setting version to '$selected'")
    writeBuildConf(selected, Version.play)
    reapply(Seq(version := selected), state)
  }

  def writeBuildConf(version: String, playVersion: String) {
    val versions =
      s"""|project.version = $version
          |play.version = $playVersion
      """.stripMargin
    IO.write(file("build.conf"), versions)
  }
}
