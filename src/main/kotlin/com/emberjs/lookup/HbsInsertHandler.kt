package com.emberjs.lookup

import com.emberjs.FullPathKey
import com.emberjs.PathKey
import com.emberjs.hbs.HbsLocalReference
import com.emberjs.utils.parentModule
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

class HbsInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (PsiTreeUtil.collectElements(item.psiElement) { it.references.find { it is HbsLocalReference } != null }.size > 0) {
            return
        }
        var path = item.getUserData(PathKey)
        val fullPath = item.getUserData(FullPathKey)
        if (path == null || fullPath == null) {
            return
        }
        if (!fullPath.endsWith("/component") && !fullPath.endsWith("/index")) {
            path = fullPath
        }
        if (context.file.virtualFile is VirtualFileWindow && !context.file.virtualFile.name.endsWith(".gjs")) {
            return
        }
        if (context.file.virtualFile is VirtualFileWindow || context.file.virtualFile.name.endsWith(".gjs")) {
            val psiManager = PsiManager.getInstance(context.project)
            var f = context.file
            if (context.file.virtualFile is VirtualFileWindow) {
                f = psiManager.findFile((context.file.virtualFile as VirtualFileWindow).delegate)!!
            }


            val names = f.children.filter { it is ES6ImportDeclaration }
                    .map { it as ES6ImportDeclaration }
                    .toTypedArray()
                    .map { it.importedBindings.map { it.name } + it.importSpecifiers.map { it.alias ?: it.name }}
                    .flatten()
                    .filterNotNull()
            if (names.contains(item.lookupString)) return
            val importStr = "import ${item.lookupString} from '$fullPath';\n"
            f.viewProvider.document!!.setText(importStr + f.viewProvider.document!!.text)
            return
        }

        val hasHbsImports = NodeModuleManager.getInstance(context.project).collectVisibleNodeModules(context.file.virtualFile).find { it.name == "ember-hbs-imports" }
        if (hasHbsImports == null) {
            return
        }

        val fullName = item.lookupString.replace("::", "/")
        val name = fullName.split("/").last()
        val pattern = "\\{\\{\\s*import\\s+([\\w*\"']+[-,\\w*\\n'\" ]+)\\s+from\\s+['\"]([^'\"]+)['\"]\\s*\\}\\}"
        val matches = Regex(pattern).findAll(context.document.text)
        val importsSq = matches.map {
            it.groups.filterNotNull().map { it.value }.toMutableList().takeLast(2).toMutableList()
        }
        val imports = importsSq.asIterable().toMutableList()
        val m = imports.indexOfFirst { it.last() == path }

        if (m != -1) {
            val groups = imports.elementAt(m)
            if (groups.first().contains(name)) return
            val g = groups.elementAt(0).replace("'", "").replace("\"", "")
            groups.removeAt(0)
            groups[0] = "'$g,$name'"
        } else {
            val l = arrayOf(name, path)
            imports.add(l.toMutableList())
        }
        var text = context.document.text
        text = text.replace(Regex("$pattern.*\n"), "")
        for (imp in imports.reversed()) {
            val l = arrayOf("{{import", imp.elementAt(0), "from '${imp.elementAt(1)}'}}")
            text = l.joinToString(" ") + "\n" + text
        }
        context.document.setText(text)
        context.commitDocument()
    }

}
