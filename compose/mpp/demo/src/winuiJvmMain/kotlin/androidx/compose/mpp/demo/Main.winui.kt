/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.titleBarLeftInset
import androidx.compose.foundation.layout.titleBarRightInset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.currentComposeViewForTest
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import windows.foundation.EventRegistrationToken
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.dispatching.DispatcherQueueTimer
import windows.foundation.TypedEventHandler
import org.jetbrains.skiko.GraphicsApi

@OptIn(InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val validation = WinUIMppSampleValidationReport.fromSystemProperties()
    val extendsContentIntoTitleBar = java.lang.Boolean.getBoolean(
        "compose.winui.mpp.sample.extendsContentIntoTitleBar"
    )
    val validateTitleBarInsets = java.lang.Boolean.getBoolean(
        "compose.winui.mpp.sample.validateTitleBarInsets"
    )
    Application {
        val applicationScope = this
        Window(
            title = "Compose MPP demo",
            state = rememberWindowState(width = 1024.dp, height = 850.dp),
            onCloseRequest = { exitApplication() },
            extendsContentIntoTitleBar = extendsContentIntoTitleBar,
        ) {
            DisposableEffect(Unit) {
                validation.record("window-content-composed")
                onDispose {
                    validation.record("window-content-disposed")
                }
            }
            SideEffect {
                val size = appWindow.size
                validation.record("window-composed")
                if (size.width > 0 && size.height > 0) {
                    validation.record("window-positive-size")
                }
                if (validateTitleBarInsets) {
                    if (window.extendsContentIntoTitleBar) {
                        validation.record("window-extends-content-into-titlebar")
                    }
                    val titleBar = appWindow.titleBar
                    if (titleBar != null) {
                        validation.record(
                            "titlebar-raw:${titleBar.height}:${titleBar.leftInset}:" +
                                titleBar.rightInset
                        )
                        if (titleBar.height > 0) {
                            validation.record("titlebar-raw-positive")
                        }
                    }
                }
            }
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val density = LocalDensity.current
            val fontsLoaded = remember { mutableStateOf(false) }
            val composeView = currentComposeViewForTest
            val app = remember { App(initialScreenName = args.getOrNull(0)) }
            val navController = rememberNavController()
            val topAppBarValidationRecorder: ((Int, Int) -> Unit)? =
                if (validateTitleBarInsets) {
                    { systemBarsTop, topAppBarHeight ->
                        validation.record("topappbar-layout:$systemBarsTop:$topAppBarHeight")
                        if (systemBarsTop > 0 && topAppBarHeight > systemBarsTop) {
                            validation.record("topappbar-extends-below-titlebar")
                        }
                    }
                } else {
                    null
                }

            if (validateTitleBarInsets) {
                val captionBarTop = WindowInsets.captionBar.getTop(density)
                val systemBarsTop = WindowInsets.systemBars.getTop(density)
                val safeDrawingTop = WindowInsets.safeDrawing.getTop(density)
                val systemBarsLeft = WindowInsets.systemBars.getLeft(density, LayoutDirection.Ltr)
                val systemBarsRight = WindowInsets.systemBars.getRight(density, LayoutDirection.Ltr)
                val titleBarLeft = WindowInsets.titleBarLeftInset.getLeft(density, LayoutDirection.Ltr)
                val titleBarRight = WindowInsets.titleBarRightInset.getRight(density, LayoutDirection.Ltr)
                SideEffect {
                    validation.record(
                        "window-insets-top:$captionBarTop:$systemBarsTop:$safeDrawingTop"
                    )
                    validation.record("window-insets-horizontal:$systemBarsLeft:$systemBarsRight")
                    validation.record("titlebar-insets-horizontal:$titleBarLeft:$titleBarRight")
                    if (captionBarTop > 0) {
                        validation.record("captionbar-inset-positive")
                    }
                    if (captionBarTop > 0 && systemBarsTop >= captionBarTop) {
                        validation.record("systembars-inset-includes-caption")
                    }
                    if (captionBarTop > 0 && safeDrawingTop >= captionBarTop) {
                        validation.record("safedrawing-inset-includes-caption")
                    }
                }
            }

            // No theme around the content: the desktop demo has none either, and the two are
            // compared screen by screen.
            if (fontsLoaded.value) {
                SideEffect {
                    validation.record("app-content-composed")
                }
                CompositionLocalProvider(
                    LocalSampleTopAppBarValidationRecorder provides topAppBarValidationRecorder
                ) {
                    app.Content(navController)
                }
            }

            LaunchedEffect(Unit) {
                val fontBytes = getResourceBytes("NotoColorEmoji.ttf")
                if (fontBytes != null) {
                    val fontFamily = FontFamily(listOf(Font("NotoColorEmoji", fontBytes)))
                    fontFamilyResolver.preload(fontFamily)
                    validation.record("font-resource-loaded")
                }
                fontsLoaded.value = true
                if (
                    java.lang.Boolean.getBoolean("compose.winui.mpp.sample.autoTraverse") ||
                    java.lang.Boolean.getBoolean("compose.winui.mpp.sample.autoExit")
                ) {
                    WinUIMppSampleAutoRunner(
                        dispatcherQueue = dispatcherQueue,
                        applicationScope = applicationScope,
                        navController = navController,
                        composeView = composeView,
                        windowSize = { appWindow.size.let { IntSize(it.width, it.height) } },
                        validation = validation,
                        autoTraverse = java.lang.Boolean.getBoolean(
                            "compose.winui.mpp.sample.autoTraverse"
                        ),
                        autoExit = java.lang.Boolean.getBoolean(
                            "compose.winui.mpp.sample.autoExit"
                        ),
                    ).start()
                }
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private fun exercisePointerPath(
    composeView: WinUIComposeView?,
    windowSize: IntSize?,
) {
    if (composeView == null) return
    val size = windowSize ?: return
    if (size.width <= 0 || size.height <= 0) return
    val center = Offset(size.width * 0.5f, size.height * 0.62f)
    val dragEnd = Offset(size.width * 0.62f, size.height * 0.62f)

    composeView.sendMouseMoveForTest(center)
    composeView.sendMouseScrollForTest(center, Offset(0f, -3f))
    composeView.sendMouseScrollForTest(center, Offset(0f, 3f))
    composeView.sendMousePressForTest(center)
    composeView.sendMouseMoveForTest(dragEnd)
    composeView.sendMouseReleaseForTest(dragEnd)
}

private data class AutoTraversalTarget(
    val path: String,
    val route: String,
)

private fun Screen.collectTraversalTargets(
    prefix: List<String> = emptyList(),
): List<AutoTraversalTarget> {
    val path = prefix + title
    return when (this) {
        is Screen.Selection -> screens.flatMap { it.collectTraversalTargets(path) }
        else -> listOf(AutoTraversalTarget(path.joinToString("/"), title))
    }
}

@OptIn(InternalComposeUiApi::class)
private fun WinUIMppSampleValidationReport.recordRenderDiagnostics(
    composeView: WinUIComposeView,
): Boolean {
    if (composeView.renderApiForTest == GraphicsApi.DIRECT3D) {
        record("render-direct3d")
    }
    val platformSize = composeView.lastRenderSizeForTest
    if (platformSize != null && platformSize.width > 0 && platformSize.height > 0) {
        record("render-positive-size")
    }
    val renderedStateSize = composeView.lastRenderedStateSizeForTest
    if (platformSize != null && renderedStateSize == platformSize) {
        record("render-state-size-matched")
    }
    val drawRect = composeView.lastDrawRectForTest
    if (drawRect.width > 0f && drawRect.height > 0f) {
        record("non-empty-draw-bounds")
    }
    return composeView.renderApiForTest == GraphicsApi.DIRECT3D &&
        platformSize != null &&
        platformSize.width > 0 &&
        platformSize.height > 0 &&
        renderedStateSize == platformSize &&
        drawRect.width > 0f &&
        drawRect.height > 0f
}

@OptIn(InternalComposeUiApi::class)
private class WinUIMppSampleAutoRunner(
    dispatcherQueue: DispatcherQueue,
    private val applicationScope: ApplicationScope,
    private val navController: NavHostController,
    private val composeView: WinUIComposeView?,
    private val windowSize: () -> IntSize?,
    private val validation: WinUIMppSampleValidationReport,
    private val autoTraverse: Boolean,
    private val autoExit: Boolean,
) {
    private val timer: DispatcherQueueTimer = dispatcherQueue.createTimer().also { timer ->
        timer.interval = 50.milliseconds
        timer.isRepeating = false
    }
    private val targets = MainScreen.collectTraversalTargets()
    private var token: EventRegistrationToken? = null
    private var initialSettleTicks = 0
    private var targetIndex = 0
    private var targetSettleTicks = 0
    private var exitSettleTicks = 0
    private var isRenderReady = false
    private var isComplete = false
    private var phase = Phase.InitialSettle
    private var tickHandler: TypedEventHandler<DispatcherQueueTimer, Any?>? = null

    fun start() {
        if (autoTraverse) {
            validation.record("autorun-start")
            validation.record("autorun-count:${targets.size}")
        } else {
            phase = Phase.ExitSettle
        }
        val handler: TypedEventHandler<DispatcherQueueTimer, Any?> = { _, _ -> tick() }
        tickHandler = handler
        token = timer.tick.add(handler)
        timer.start()
    }

    private fun tick() {
        try {
            val currentComposeView = composeView
            if (currentComposeView != null) {
                isRenderReady = validation.recordRenderDiagnostics(currentComposeView) ||
                    isRenderReady
            }
            if (isComplete) return
            if (autoTraverse && isRenderReady && phase != Phase.ExitSettle) {
                traverseAllTargets(currentComposeView)
                phase = Phase.ExitSettle
            }
            when (phase) {
                Phase.InitialSettle -> {
                    initialSettleTicks += 1
                    if (initialSettleTicks >= 4) {
                        phase = Phase.Navigate
                    }
                }
                Phase.Navigate -> navigateCurrentTarget()
                Phase.AfterNavigateSettle -> {
                    targetSettleTicks += 1
                    if (targetSettleTicks >= 4) {
                        exercisePointerPath(currentComposeView, windowSize())
                        targetSettleTicks = 0
                        phase = Phase.AfterPointerSettle
                    }
                }
                Phase.AfterPointerSettle -> {
                    targetSettleTicks += 1
                    if (targetSettleTicks >= 4) {
                        val target = targets[targetIndex]
                        validation.record("autorun-ok:$targetIndex:${target.path}")
                        targetIndex += 1
                        targetSettleTicks = 0
                        phase = Phase.Navigate
                    }
                }
                Phase.ExitSettle -> settleExit()
            }
        } finally {
            if (!isComplete) {
                timer.start()
            }
        }
    }

    private fun traverseAllTargets(currentComposeView: WinUIComposeView?) {
        while (targetIndex < targets.size) {
            val target = targets[targetIndex]
            validation.record("autorun-enter:$targetIndex:${target.path}")
            val navigated = runCatching {
                navController.navigate(target.route) {
                    launchSingleTop = true
                    popUpTo(MainScreen.title) {
                        inclusive = false
                    }
                }
            }.onFailure { throwable ->
                validation.record(
                    "autorun-navigation-failed:$targetIndex:${target.path}:" +
                        "${throwable::class.qualifiedName}:${throwable.message}"
                )
            }.isSuccess
            if (navigated) {
                exercisePointerPath(currentComposeView, windowSize())
                validation.record("autorun-ok:$targetIndex:${target.path}")
            }
            targetIndex += 1
        }
        validation.record("autorun-complete")
    }

    private fun navigateCurrentTarget() {
        if (targetIndex >= targets.size) {
            validation.record("autorun-complete")
            phase = Phase.ExitSettle
            if (!autoExit) {
                complete()
            }
            return
        }
        val target = targets[targetIndex]
        validation.record("autorun-enter:$targetIndex:${target.path}")
        runCatching {
            navController.navigate(target.route) {
                launchSingleTop = true
                popUpTo(MainScreen.title) {
                    inclusive = false
                }
            }
        }.onFailure { throwable ->
            validation.record(
                "autorun-navigation-failed:$targetIndex:${target.path}:" +
                    "${throwable::class.qualifiedName}:${throwable.message}"
            )
            targetIndex += 1
            return
        }
        targetSettleTicks = 0
        phase = Phase.AfterNavigateSettle
    }

    private fun settleExit() {
        if (!autoExit) {
            complete()
            return
        }
        exitSettleTicks += 1
        if (!isRenderReady && exitSettleTicks < 100) return
        validation.record("frame-observed")
        validation.record("exit-requested")
        complete()
        applicationScope.exitApplication()
    }

    private fun complete() {
        if (isComplete) return
        isComplete = true
        timer.stop()
        token?.let { runCatching { timer.tick.remove(it) } }
        token = null
        tickHandler = null
    }

    private enum class Phase {
        InitialSettle,
        Navigate,
        AfterNavigateSettle,
        AfterPointerSettle,
        ExitSettle,
    }
}

suspend fun getResourceBytes(resourceName: String): ByteArray? = withContext(Dispatchers.IO) {
    val classLoader = Thread.currentThread().contextClassLoader
    try {
        classLoader.getResourceAsStream(resourceName).use { inputStream ->
            return@withContext inputStream?.readBytes()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        return@withContext null
    }
}

private class WinUIMppSampleValidationReport(
    private val file: File?,
) {
    private val events = linkedSetOf<String>()

    fun record(event: String) {
        if (file == null) return
        synchronized(events) {
            if (!events.add(event)) return
            file.parentFile?.mkdirs()
            file.writeText(events.joinToString(separator = System.lineSeparator()))
        }
    }

    companion object {
        fun fromSystemProperties(): WinUIMppSampleValidationReport =
            WinUIMppSampleValidationReport(
                System.getProperty("compose.winui.mpp.sample.validationReport")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
            )
    }
}
