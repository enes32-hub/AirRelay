package io.github.jqssun.airplay.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val LocalReducedMotion = staticCompositionLocalOf { false }

private data class ProductPresentationEnvironment(
    val context: Context, val configuration: Configuration, val density: Density,
)

// Dialog's new Android Compose owner replaces platform locals. This custom local
// crosses that boundary so its content can retain the same product presentation.
private val LocalProductPresentation = staticCompositionLocalOf<ProductPresentationEnvironment?> { null }

/** UI language only: no Activity recreation, codec reset or native language renegotiation. */
@Composable
fun ProductEnvironment(language: String, largeText: Boolean, reduceMotion: Boolean,
    content: @Composable () -> Unit) {
    val base = LocalContext.current
    val current = LocalConfiguration.current
    val config = remember(current, language) {
        io.github.jqssun.airplay.receiver.AppLanguages.configuration(current, language)
    }
    val context = remember(base, config) {
        object : ContextWrapper(base) {
            private val localized = base.createConfigurationContext(config).resources
            override fun getResources(): Resources = localized
        }
    }
    val density = LocalDensity.current
    val productDensity = remember(density.density, density.fontScale, largeText) {
        Density(density.density, density.fontScale * if (largeText) 1.12f else 1f)
    }
    val presentation = remember(context, config, productDensity) {
        ProductPresentationEnvironment(context, config, productDensity)
    }
    CompositionLocalProvider(LocalProductPresentation provides presentation,
        LocalReducedMotion provides reduceMotion,
    ) {
        RestoreProductPresentation(content)
    }
}

@Composable
private fun RestoreProductPresentation(content: @Composable () -> Unit) {
    val presentation = LocalProductPresentation.current
    if (presentation == null) content()
    else CompositionLocalProvider(LocalContext provides presentation.context,
        LocalConfiguration provides presentation.configuration,
        LocalDensity provides presentation.density,
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (presentation.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
                androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
        content = content)
}

/** Restore only presentation locals; dialog-owned view, focus and input remain intact. */
@Composable
internal fun ProductDialog(onDismissRequest: () -> Unit, properties: DialogProperties,
    content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        RestoreProductPresentation(content)
    }
}

/** Material AlertDialog owns its Dialog internally, so restore inside each content slot. */
@Composable
internal fun ProductAlertDialog(onDismissRequest: () -> Unit,
    title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit,
    shape: Shape = AlertDialogDefaults.shape, containerColor: Color = AlertDialogDefaults.containerColor) {
    AlertDialog(onDismissRequest = onDismissRequest, shape = shape, containerColor = containerColor,
        title = { RestoreProductPresentation(title) },
        text = { RestoreProductPresentation(text) },
        confirmButton = { RestoreProductPresentation(confirmButton) },
        dismissButton = { RestoreProductPresentation(dismissButton) })
}
