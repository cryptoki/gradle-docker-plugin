import de.gesellix.docker.client.DockerClientImpl

buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath("de.gesellix:docker-client:2021-08-07T20-46-00")
  }
}

tasks.register("checkDockerAvailability") {
  val client = DockerClientImpl()

  doFirst {
    logger.lifecycle("Docker Host:\n|> ${client.env.dockerHost} <|")
  }
  doLast {
    val pingResponse = client.ping()
    logger.lifecycle("Docker Ping:\n|> $pingResponse <|")

    if (pingResponse.status.success) {
      logger.lifecycle("Docker Version:\n|> ${client.version().content} <|")
      logger.lifecycle("Docker Info:\n|> ${client.info().content} <|")
    } else {
      logger.warn("Docker Engine not available (ping failed).")
    }
  }
}
