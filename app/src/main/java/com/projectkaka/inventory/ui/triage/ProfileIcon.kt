package com.projectkaka.inventory.ui.triage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Top-right profile affordance.
 *
 * When [hasEmergency] is true the icon is wrapped in a 2.dp red ring. Tapping it
 * always opens the triage sheet; the ring is the signal, not the gate.
 */
@Composable
fun ProfileIcon(
    hasEmergency: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (hasEmergency) {
                    Modifier.border(2.dp, Color(0xFFE74C3C), CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Triage",
            tint = if (hasEmergency) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}
