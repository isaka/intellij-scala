resolvers += Resolver.url("jetbrains-sbt", url(s"http://dl.bintray.com/jetbrains/sbt-plugins"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.0.0")
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "2.2.5-noauto+1-cb7f4b59")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
