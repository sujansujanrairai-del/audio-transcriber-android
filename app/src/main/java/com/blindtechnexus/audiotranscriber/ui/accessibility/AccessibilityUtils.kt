package com.blindtechnexus.audiotranscriber.ui.accessibility

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AccessibilityDefaults {
    val StandardTouchTarget: Dp = 48.dp
    val LargeTouchTarget: Dp = 56.dp
}

fun Modifier.accessibleTouchTarget(isLargeTarget: Boolean = false): Modifier = this.then(
    Modifier.defaultMinSize(
        minWidth = if (isLargeTarget) AccessibilityDefaults.LargeTouchTarget else AccessibilityDefaults.StandardTouchTarget,
        minHeight = if (isLargeTarget) AccessibilityDefaults.LargeTouchTarget else AccessibilityDefaults.StandardTouchTarget
    )
)

fun Modifier.transcribeLiveRegion(enabled: Boolean = true): Modifier = if (enabled) {
    this.semantics {
        liveRegion = LiveRegionMode.Polite
    }
} else this

fun Modifier.speakerHeading(speakerName: String): Modifier = this.semantics {
    heading()
    contentDescription = "Speaker turn: $speakerName"
}

fun Modifier.transcriptSegmentSemantics(
    speaker: String,
    text: String,
    timestamp: String,
    onCopyAction: (() -> Boolean)? = null
): Modifier = this.semantics {
    contentDescription = "At $timestamp, $speaker said: $text"
    if (onCopyAction != null) {
        customActions = listOf(
            CustomAccessibilityAction(label = "Copy segment text", action = onCopyAction)
        )
    }
}
