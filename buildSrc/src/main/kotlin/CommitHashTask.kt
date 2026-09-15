import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class CommitHashTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Inject
    abstract val exec: ExecOperations

    init {
        group = "build"
        description = "Write commit hash / version to file"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun executeTask() {
        val out = ByteArrayOutputStream()
        exec.exec {
            commandLine = "git rev-parse HEAD".split(" ")
            standardOutput = out
            workingDir = layout.projectDirectory.asFile
        }
        val commitHash = out.toString().trim()
        check(commitHash.matches(Regex("[0-9a-f]{40}"))) { "Invalid commit hash: $commitHash" }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            println("Writing commit hash: $commitHash")
            writeText(commitHash)
        }
    }
}
