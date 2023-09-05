package com.github.edwardGunawan
import sbt.ModuleID
import sbt._

object CompilerPlugins {

  object KindProjector {
    val core: ModuleID = "org.typelevel" %% "kind-projector" % "0.10.3"
  }

  object BetterMonadicFor {
    val core: ModuleID = "com.olegpy" %% "better-monadic-for" % "0.3.1"
  }

  object MacroParadise {
    val core: ModuleID = "org.scalamacros" % "paradise" % "2.1.1"
  }
}
