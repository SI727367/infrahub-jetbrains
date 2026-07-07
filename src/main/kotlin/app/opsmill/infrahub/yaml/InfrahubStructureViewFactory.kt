package app.opsmill.infrahub.yaml

import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.yaml.snakeyaml.Yaml
import java.io.File

class InfrahubStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (!isSchemaYaml(psiFile.project, psiFile.virtualFile)) {
            return null
        }

        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return InfrahubStructureViewModel(psiFile)
            }
        }
    }

    private fun isSchemaYaml(project: Project, file: VirtualFile?): Boolean {
        if (file == null) return false
        val schemaDir = resolveSchemaDirectory(project) ?: return false
        return file.path.startsWith(schemaDir.absolutePath) && (file.extension == "yml" || file.extension == "yaml")
    }

    private fun resolveSchemaDirectory(project: Project): File? {
        val basePath = project.basePath ?: return null
        val configured = InfrahubSettingsState.getInstance().schemaDirectory
        val dir = File(configured)
        return if (dir.isAbsolute) dir else File(basePath, configured)
    }
}

class InfrahubStructureViewModel(psiFile: PsiFile) : StructureViewModelBase(psiFile, InfrahubStructureRootElement(psiFile)) {
    init {
        withSuitableClasses(InfrahubStructureItemElement::class.java, InfrahubLeafElement::class.java)
    }

    override fun getSorters(): Array<out Sorter> = arrayOf(Sorter.ALPHA_SORTER)
}

class InfrahubStructureRootElement(psiFile: PsiFile) : PsiTreeElementBase<PsiFile>(psiFile) {

    private val yaml = Yaml()

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val file = element?.virtualFile ?: return emptyList()
        val content = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return emptyList()
        val root = runCatching { yaml.load<Any>(content) }.getOrNull() as? Map<*, *> ?: return emptyList()

        return buildList {
            listOf("nodes", "generics").forEach { section ->
                val items = root[section] as? List<*> ?: return@forEach
                items.forEach { item ->
                    val map = item as? Map<*, *> ?: return@forEach
                    val namespace = map["namespace"]?.toString().orEmpty()
                    val name = map["name"]?.toString().orEmpty()
                    val label = if (namespace.isNotBlank()) "$namespace$name" else name
                    add(InfrahubStructureItemElement(element, label, map))
                }
            }
        }
    }

    override fun getPresentableText(): String? = element?.name
}

class InfrahubStructureItemElement(
    psiFile: PsiFile?,
    private val label: String,
    private val data: Map<*, *>
) : PsiTreeElementBase<PsiFile>(psiFile), SortableTreeElement {

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val children = mutableListOf<StructureViewTreeElement>()
        val attributes = data["attributes"] as? List<*> ?: emptyList<Any>()
        attributes.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val name = map["name"]?.toString() ?: return@forEach
            val kind = map["kind"]?.toString().orEmpty()
            children.add(InfrahubLeafElement(element, if (kind.isNotBlank()) "$name: $kind" else name))
        }
        val relationships = data["relationships"] as? List<*> ?: emptyList<Any>()
        relationships.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val name = map["name"]?.toString() ?: return@forEach
            val peer = map["peer"]?.toString().orEmpty()
            children.add(InfrahubLeafElement(element, if (peer.isNotBlank()) "$name -> $peer" else name))
        }
        return children
    }

    override fun getPresentableText(): String = label

    override fun getAlphaSortKey(): String = label
}

class InfrahubLeafElement(
    psiFile: PsiFile?,
    private val label: String
) : PsiTreeElementBase<PsiFile>(psiFile), SortableTreeElement {
    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()

    override fun getPresentableText(): String = label

    override fun getAlphaSortKey(): String = label
}
