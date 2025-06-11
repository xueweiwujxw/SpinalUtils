ThisBuild / version := "0.1.4"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / organization := "io.github.xueweiwujxw"

val spinalVersion = "1.12.2"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalIdslPlugin = compilerPlugin(
  "com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion
)

description := "Utils collection based on SpinalHDL"
homepage := Some(url("https://github.com/xueweiwujxw/SpinalUtils"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/xueweiwujxw/SpinalUtils"),
    "scm:git@github.com:xueweiwujxw/SpinalUtils.git"
  )
)
developers := List(
  Developer(id = "xueweiwujxw", name = "Wlanxww", email = "xueweiwujxw@outlook.com", url = url("https://wlanxww.com"))
)
licenses := Seq("BSD 3-Clause" -> new URL("https://opensource.org/licenses/BSD-3-Clause"))

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
