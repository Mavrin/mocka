import sbt._

import Keys._
import AndroidKeys._
import AndroidNdkKeys._

object General {
  // Some basic configuration
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Mocka",
    version := "0.1",
    versionCode := 0,
    scalaVersion := "2.10.1",
    platformName in Android := "android-16",
    keyalias in Android := "change-me"
  )

  // Default Proguard settings
  lazy val proguardSettings = inConfig(Android) (Seq (
    useProguard := false,
    skipScalaLibrary := true,
    predexLibraries := true,
    proguardOptimizations += "-keep class scala.collection.SeqLike { public java.lang.String toString(); }",
    proguardOptimizations += "-keep class com.github.fxthomas.mocka.** { *; }"
  ))

  // Example NDK settings
  lazy val ndkSettings = AndroidNdk.settings ++ inConfig(Android) (Seq(
    jniClasses := Seq(),
    javahOutputFile := Some(new File("native.h"))
  ))

  // Library dependencies
  lazy val librarySettings = Seq(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      "org.scaloid" % "scaloid" % "1.1_8_2.10"
    )
  )

  // Full Android settings
  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    proguardSettings ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++
    librarySettings
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "main",
    file("."),
    settings = General.fullAndroidSettings ++ AndroidEclipseDefaults.settings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++
               AndroidEclipseDefaults.settings ++
               AndroidTest.androidSettings ++
               General.proguardSettings ++ Seq (
      name := "MockaTests"
    )
  ) dependsOn main
}
