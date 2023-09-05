package com.github.edwardGunawan
import sbt.ModuleID
import sbt._

object Dependencies {

  object Scala {
    val v12 = "2.12.10"
    val v13 = "2.13.1"
  }

  object AWSSDKV2 {
    private val version = "2.16.59"
    val core: ModuleID = "software.amazon.awssdk" % "core" % version
    val sts: ModuleID = "software.amazon.awssdk" % "sts" % version
    val dynamodb: ModuleID = "software.amazon.awssdk" % "dynamodb" % version
  }

  object AWSLambdaRuntime {
    val core: ModuleID = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
    val event: ModuleID = "com.amazonaws" % "aws-lambda-java-events" % "3.8.0"
  }

  object Cats {
    private val version = "2.6.0"
    val core: ModuleID = "org.typelevel" %% "cats-core" % version
    val kernel: ModuleID = "org.typelevel" %% "cats-kernel" % version
    val testEffect: ModuleID = "com.codecommit" %% "cats-effect-testing-scalatest" % "0.5.3"
  }

  object CatsEffect {
    private val version = "2.4.1"
    val core: ModuleID = "org.typelevel" %% "cats-effect" % version
  }

  object CatsMtl {
    private val version = "1.1.3"
    val core: ModuleID = "org.typelevel" %% "cats-mtl" % version
  }

  object Log4Cats {
    private val version = "1.3.0"
    val slf4j: ModuleID = "org.typelevel" %% "log4cats-slf4j" % version
    val core: ModuleID = "org.typelevel" %% "log4cats-core" % version
  }

  object Ciris {
    private val version = "1.2.1"
    val core: ModuleID = "is.cir" %% "ciris" % version
    val refined: ModuleID = "is.cir" %% "ciris-refined" % version
  }

  object Circe {
    private val version = "0.13.0"
    val core: ModuleID = "io.circe" %% "circe-core" % version
    val parser: ModuleID = "io.circe" %% "circe-paraser" % version
    val config: ModuleID = "io.circe" %% "circe-config" % "0.8.0"
    val generic: ModuleID = "io.circe" %% "circe-generic" % version
    val literal: ModuleID = "io.circe" %% "circe-literal" % version
    val genericExtra: ModuleID = "io.circe" %% "circe-generic-extras" % version
  }

  object FS2 {
    private val version = "2.5.4"
    val core: ModuleID = "co.fs2" %% "fs2-core" % version
    val io: ModuleID = "co.fs2" %% "fs2-io" % version
    val reactiveStream: ModuleID = "co.fs2" %% "fs2-reactive-streams" % version
  }

  object Http4s {
    private val version = "0.21.22"
    val dsl: ModuleID = "org.http4s" %% "http4s-dsl" % version
    val client: ModuleID = "org.http4s" %% "http4s-blaze-client" % version
    val server: ModuleID = "org.http4s" %% "http4s-blaze-server" % version
    val circe: ModuleID = "org.http4s" %% "http4s-circe" % version
    val xml: ModuleID = "org.http4s" %% "http4s-scala-xml" % version
  }

  object JSoup {
    private val version = "1.13.1"
    val core: ModuleID = "org.jsoup" % "jsoup" % version
  }

  object LogBack {
    private val version = "1.2.3"
    val core: ModuleID = "ch.qos.logback" % "logback-classic" % version
  }

  object MUnit {
    private val metaVersion = "0.7.20"
    private val munitCatsEffectVersion = "0.13.0"
    val meta: ModuleID = "org.scalameta" %% "munit" % metaVersion
    val munitCatsEffect: ModuleID = "org.typelevel" %% "munit-cats-effect-2" % munitCatsEffectVersion
  }

  object SvmSubs {
    private val version = "20.2.0"
    val core: ModuleID = "org.scalameta" %% "svm-subs" % version
  }

  object ScalaXML {
    private val version = "1.3.0"
    val core: ModuleID = "org.scala-lang.modules" %% "scala-xml" % version
  }

  object ScalaTest {
    private val version = "3.2.5"
    val core: ModuleID = "org.scalatest" %% "scalatest" % version
  }

  object ScalaMock {
    private val version = "5.1.0"
    val core: ModuleID = "org.scalamock" %% "scalamock" % version
  }

}
