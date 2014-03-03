name := "squeryl-activiti"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.squeryl" %% "squeryl" % "0.9.5-6",
  "org.activiti" % "activiti-engine" % "5.14",
  "org.specs2" %% "specs2" % "2.3.8" % "test",
  "com.h2database" % "h2" % "1.3.175" % "test")

resolvers ++= Seq(
  "Alfresco Maven" at "https://maven.alfresco.com/nexus/content/groups/public/")
