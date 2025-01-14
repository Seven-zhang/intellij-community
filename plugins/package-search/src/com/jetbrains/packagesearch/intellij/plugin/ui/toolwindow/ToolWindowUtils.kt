/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.castSafelyTo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.HasToolWindowActions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithToolWindowActionsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories.RepositoryManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.util.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.util.addSelectionChangedListener
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lookAndFeelFlow
import com.jetbrains.packagesearch.intellij.plugin.util.onEach
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

internal fun ToolWindow.initialize(project: Project) {
    title = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

    contentManager.addSelectionChangedListener { event ->
        if (this is ToolWindowEx) {
            setAdditionalGearActions(null)
            event.content.component.castSafelyTo<HasToolWindowActions>()
                ?.also { setAdditionalGearActions(it.gearActions) }
        }
        setTitleActions(emptyList())
        event.content.component.castSafelyTo<HasToolWindowActions>()
            ?.titleActions
            ?.also { setTitleActions(it.toList()) }
    }

    contentManager.removeAllContents(true)

    val panels = buildList {
        add(PackageManagementPanel(project))
        if (FeatureFlags.showRepositoriesTab) {
            add(RepositoryManagementPanel(project))
        }
    }

    val contentFactory = ContentFactory.getInstance()

    for (panel in panels) {
        panel.initialize(contentManager, contentFactory)
    }

    isAvailable = false

    project.packageSearchProjectService.projectModulesStateFlow
        .map { it.isNotEmpty() }
        .onEach { logInfo("PackageSearchToolWindowFactory#packageSearchModulesChangesFlow") { "Setting toolWindow.isAvailable = $it" } }
        .onEach(Dispatchers.EDT) { isAvailable = it }
        .launchIn(project.lifecycleScope)

    combine(
        project.lookAndFeelFlow,
        project.packageSearchProjectService.projectModulesStateFlow.filter { it.isNotEmpty() }
    ) { _, _ -> withContext(Dispatchers.EDT) { contentManager.component.updateAndRepaint() } }
        .launchIn(project.lifecycleScope)
}

internal fun PackageSearchPanelBase.initialize(
    contentManager: ContentManager,
    contentFactory: ContentFactory,
) {
    val panelContent = content // should be executed before toolbars
    val toolbar = toolbar
    val topToolbar = topToolbar
    val gearActions = gearActions
    val titleActions = titleActions

    if (topToolbar == null) {
        contentManager.addTab(title, panelContent, toolbar, gearActions, titleActions, contentFactory, this)
    } else {
        val content = contentFactory.createContent(
            toolbar?.let {
                SimpleToolWindowWithTwoToolbarsPanel(
                    it,
                    topToolbar,
                    gearActions,
                    titleActions,
                    panelContent
                )
            },
            title,
            false
        )

        content.isCloseable = false
        contentManager.addContent(content)
        content.component.updateAndRepaint()
    }
}

internal fun ContentManager.addTab(
    @Nls title: String,
    content: JComponent,
    toolbar: JComponent?,
    gearActions: ActionGroup?,
    titleActions: Array<AnAction>?,
    contentFactory: ContentFactory,
    provider: DataProvider
) {
    addContent(
        contentFactory.createContent(null, title, false).apply {
            component = SimpleToolWindowWithToolWindowActionsPanel(gearActions, titleActions, false, provider = provider).apply {
                setProvideQuickActions(true)
                setContent(content)
                toolbar?.let { setToolbar(it) }
                isCloseable = false
            }
        }
    )
}

@Suppress("UnusedReceiverParameter")
internal fun Dispatchers.toolWindowManager(project: Project): CoroutineDispatcher = object : CoroutineDispatcher() {

    private val executor = ToolWindowManager.getInstance(project)

    override fun dispatch(context: CoroutineContext, block: Runnable) = executor.invokeLater(block)
}
