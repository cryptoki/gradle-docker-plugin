package de.gesellix.gradle.docker.tasks

import de.gesellix.docker.client.DockerClient
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class DockerBuildTaskSpec extends Specification {

    def project
    def task
    def dockerClient = Mock(DockerClient)

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.task('dockerBuild', type: DockerBuildTask)
        task.dockerClient = dockerClient
    }

    @Unroll
    def "depends on tar task to archive buildContextDirectory (as #type)"() {
        given:
        task.buildContextDirectory = baseDir
        task.imageName = "user/imageName"

        when:
        task.configure()

        then:
        project.getTasksByName("tarBuildcontextForDockerBuild", false).size() == 1
        and:
        task.dependsOn.any { it == project.getTasksByName("tarBuildcontextForDockerBuild", false).first() }

        where:
        baseDir                                                                             | type
        parentDir(getClass().getResource('/docker/Dockerfile'))                             | File
        parentDir(getClass().getResource('/docker/Dockerfile')).absolutePath                | String
        wrapInClosure(parentDir(getClass().getResource('/docker/Dockerfile')).absolutePath) | 'lazily resolved String'
    }

    def "tar task must run after dockerBuild dependencies"() {
        URL dockerfile = getClass().getResource('/docker/Dockerfile')
        def baseDir = new File(dockerfile.toURI()).parentFile

        given:
        def buildTaskDependency = project.task('buildTaskDependency', type: TestTask)
        task.dependsOn buildTaskDependency
        task.buildContextDirectory = baseDir
        task.imageName = "busybox"

        when:
        task.configure()

        then:
        project.tasks.findByName("dockerBuild").getDependsOn().contains project.tasks.findByName("buildTaskDependency")
        and:
        def tarBuildcontextForDockerBuild = project.tasks.findByName("tarBuildcontextForDockerBuild")
        tarBuildcontextForDockerBuild.getMustRunAfter().values.contains project.tasks.findByName("buildTaskDependency")
    }

    def "tar of buildContextDirectory contains buildContextDirectory"() {
        URL dockerfile = getClass().getResource('/docker/Dockerfile')
        def baseDir = new File(dockerfile.toURI()).parentFile

        given:
        task.buildContextDirectory = baseDir
        task.imageName = "user/imageName"

        when:
        task.configure()

        then:
        def tarOfBuildcontextTask = project.getTasksByName("tarBuildcontextForDockerBuild", false).first()
//    tarOfBuildcontextTask.destinationDir == new File("${tarOfBuildcontextTask.getTemporaryDir()}")

//    and:
//    tarOfBuildcontextTask.inputs.files.asPath == project.fileTree(baseDir).asPath

        and:
        tarOfBuildcontextTask.outputs.files.asPath == task.targetFile.absolutePath
    }

    // TODO this should become an integration test, so that the 'task dependsOn tarOfBuildcontext' also works
    def "delegates to dockerClient with tar of buildContextDirectory as buildContext"() {
        URL dockerfile = getClass().getResource('/docker/Dockerfile')
        def baseDir = new File(dockerfile.toURI()).parentFile

        given:
        task.buildContextDirectory = baseDir
        task.imageName = "user/imageName"
        task.dockerClient = dockerClient
        task.configure()
        def tarOfBuildcontextTask = project.getTasksByName("tarBuildcontextForDockerBuild", false).first()
        tarOfBuildcontextTask.execute()

        when:
        task.execute()

        then:
        1 * dockerClient.build({ FileInputStream }, [t: 'user/imageName', rm: true])
    }

    def "delegates to dockerClient with buildContext"() {
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContext = inputStream
        task.imageName = "imageName"

        when:
        task.execute()

        then:
        1 * dockerClient.build(inputStream, [rm: true, t: "imageName"]) >> "4711"

        and:
        task.outputs.files.isEmpty()
    }

    def "delegates to dockerClient with buildContext and buildParams"() {
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContext = inputStream
        task.buildParams = [rm: true, dockerfile: './custom.Dockerfile']
        task.imageName = "imageName"

        when:
        task.execute()

        then:
        1 * dockerClient.build(inputStream, [rm: true, t: "imageName", dockerfile: './custom.Dockerfile']) >> "4711"

        and:
        task.outputs.files.isEmpty()
    }


    def "does not override rm build param if given"() {
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContext = inputStream
        task.buildParams = [rm: false, dockerfile: './custom.Dockerfile']
        task.imageName = "imageName"

        when:
        task.execute()

        then:
        1 * dockerClient.build(inputStream, [rm: false, t: "imageName", dockerfile: './custom.Dockerfile']) >> "4711"

        and:
        task.outputs.files.isEmpty()
    }

    def "delegates to dockerClient with buildContext (with logs)"() {
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContext = inputStream
        task.imageName = "imageName"
        task.enableBuildLog = true

        when:
        task.execute()

        then:
        1 * dockerClient.buildWithLogs(inputStream, [rm: true, t: "imageName"]) >> [imageId: "4711", logs: []]

        and:
        task.outputs.files.isEmpty()
    }

    def "delegates to dockerClient with buildContext and buildParams (with logs)"() {
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContext = inputStream
        task.buildParams = [rm: true, dockerfile: './custom.Dockerfile']
        task.imageName = "imageName"
        task.enableBuildLog = true

        when:
        task.execute()

        then:
        1 * dockerClient.buildWithLogs(inputStream, [rm: true, t: "imageName", dockerfile: './custom.Dockerfile']) >>
                [imageId: "4711", logs: []]

        and:
        task.outputs.files.isEmpty()
    }

    // TODO this should become an integration test
    def "accepts only task configs with at least one of buildContext or buildContextDirectory"() {
        given:
        task.buildContextDirectory = null
        task.buildContext = null

        when:
        task.execute()

        then:
        Exception exception = thrown()
        exception.message == "Execution failed for task ':dockerBuild'."
        and:
        exception.cause.message ==~ "assert getBuildContext\\(\\)\n\\s{7}\\|\n\\s{7}null"
    }

    // TODO this should become an integration test
    def "accepts exactly one of buildContext or buildContextDirectory"() {
        URL dockerfile = getClass().getResource('/docker/Dockerfile')
        def baseDir = new File(dockerfile.toURI()).parentFile
        def inputStream = new FileInputStream(File.createTempFile("docker", "test"))

        given:
        task.buildContextDirectory = baseDir
        task.buildContext = inputStream

        when:
        task.execute()

        then:
        Exception exception = thrown()
        exception.message == "Execution failed for task ':dockerBuild'."
        and:
        exception.cause.message ==~ "assert !getBuildContext\\(\\)\n\\s{7}\\|\\|\n\\s{7}\\|java.io.FileInputStream@\\w+\n\\s{7}false"
    }

    def "normalizedImageName should match [a-z0-9-_.]"() {
        expect:
        task.getNormalizedImageName() ==~ "[a-z0-9-_\\.]+"
    }

    def parentDir(URL resource) {
        new File(resource.toURI()).parentFile
    }

    def wrapInClosure(value) {
        new Closure(null) {

            @Override
            Object call() {
                value
            }
        }
    }
}
