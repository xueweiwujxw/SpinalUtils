ThisBuild / version := "0.1.2"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / organization := "com.github.xueweiwujxw"

val spinalVersion = "1.11.0"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalIdslPlugin = compilerPlugin(
  "com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion
)

lazy val spinalUtils = (project in file("."))
  .settings(
    name := "spinalUtils",
    libraryDependencies ++= Seq(
      spinalCore,
      spinalLib,
      spinalIdslPlugin
    ),
    scalacOptions ++= Seq("-deprecation")
  )

fork := true
