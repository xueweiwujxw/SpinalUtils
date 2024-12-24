ThisBuild / version := "0.0.0"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / organization := "com.github.xueweiwujxw"

val spinalVersion = "1.11.0"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalIdslPlugin = compilerPlugin(
  "com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion
)

val scoptlib = "com.github.scopt" %% "scopt" % "4.1.0"

lazy val spinalUtils = (project in file("."))
  .settings(
    name := "spinalUtils",
    libraryDependencies ++= Seq(
      spinalCore,
      spinalLib,
      spinalIdslPlugin,
      scoptlib
    )
  )

fork := true
