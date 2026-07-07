package app.opsmill.infrahub.yaml

import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.yaml.snakeyaml.Yaml
import java.io.File

class InfrahubGotoDeclarationHandler : GotoDeclarationHandler {

    private val yaml = Yaml()

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val project = element.project
        val file = element.containingFile?.virtualFile ?: return null
        if (!isSchemaYaml(project, file)) {
            return null
        }

        val reference = element.text.trim().trim('"', '\'', ' ', ':')
        if (!reference.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {
            return null
        }

        val target = findSchemaDefinition(project, reference) ?: return null
        val targetFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.file) ?: return null
        val targetPsiFile = element.manager.findFile(targetFile) ?: return null
        val descriptor = OpenFileDescriptor(project, targetFile, target.line, 0)
        descriptor.navigate(true)
        return arrayOf(targetPsiFile.findElementAt(0) ?: targetPsiFile)
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

    private fun isSchemaYaml(project: Project, file: VirtualFile): Boolean {
        val schemaDir = resolveSchemaDirectory(project) ?: return false
        return file.path.startsWith(schemaDir.absolutePath) && (file.extension == "yml" || file.extension == "yaml")
    }

    private fun resolveSchemaDirectory(project: Project): File? {
        val basePath = project.basePath ?: return null
        val configured = InfrahubSettingsState.getInstance().schemaDirectory
        val dir = File(configured)
        return if (dir.isAbsolute) dir else File(basePath, configured)
    }

    private fun findSchemaDefinition(project: Project, reference: String): SchemaDefinitionTarget? {
        val schemaDir = resolveSchemaDirectory(project) ?: return null
        if (!schemaDir.exists()) {
            return null
        }

        schemaDir.walkTopDown()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
            .forEach { file ->
                val content = runCatching { file.readText() }.getOrNull() ?: return@forEach
                val root = runCatching { yaml.load<Any>(content) }.getOrNull() as? Map<*, *> ?: return@forEach
                listOf("nodes", "generics").forEach { section ->
                    val items = root[section] as? List<*> ?: return@forEach
                    items.forEach { item ->
                        val map = item as? Map<*, *> ?: return@forEach
                        val namespace = map["namespace"]?.toString().orEmpty()
                        val name = map["name"]?.toString().orEmpty()
                        if ((namespace + name).equals(reference, ignoreCase = true)) {
                            val line = findLineNumber(content, name)
                            return SchemaDefinitionTarget(file, line)
                        }
                    }
                }
            }

        return null
    }

    private fun findLineNumber(content: String, name: String): Int {
        val lines = content.lines()
        val index = lines.indexOfFirst { it.contains("name:") && it.contains(name) }
        return if (index >= 0) index else 0
    }
}

data class SchemaDefinitionTarget(
    val file: File,
    val line: Int
)
