plugins {
    scala
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val scala3Version = "3.9.0"
val scalatestVersion = "3.2.20"
val catsEffectVersion = "3.7.1"

dependencies {
    implementation(project(":core"))
    implementation("org.scala-lang:scala3-library_3:$scala3Version")
    implementation("org.typelevel:cats-effect_3:$catsEffectVersion")

    testImplementation("org.scalatest:scalatest_3:$scalatestVersion")
    testRuntimeOnly("org.scalatestplus:junit-5-14_3:3.2.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.vishwassp01.relevanceeval.scala.example.ScalaExample")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
