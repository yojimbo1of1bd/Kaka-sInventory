package com.projectkaka.inventory.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FILTER_HINTS = listOf(
    "c/electronics" to "category",
    "c/prescriptions" to "documents",
    "l/bedroom" to "location",
    "v/>500" to "value",
    "m/due" to "maintenance"
)

// ── Fixed colors for the white search bar ──
private val SearchBarBg = Color.White
private val SearchBarText = Color(0xFF1E242B)
private val SearchBarHint = Color(0xFF8A8A8A)
private val SearchBarIcon = Color(0xFF5A6A7A)
private val SearchBarBorder = Color(0xFFD0D7DE)
private val SearchBarErrorBorder = Color(0xFFE74C3C)

/**
 * Single-line command bar for the inventory query language and financial commands.
 * Typing f/ triggers financial commands; kaka show/ledger triggers navigation.
 * Pressing Enter (Done) on the keyboard submits the command.
 *
 * Always rendered white with dark text for high contrast against the dark theme.
 */
@Composable
fun MagicInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(SearchBarBg)
                .border(
                    width = if (error != null) 2.dp else 1.dp,
                    color = if (error != null) SearchBarErrorBorder else SearchBarBorder,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = SearchBarIcon
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search items, c/category, l/location...",
                        color = SearchBarHint,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = SearchBarText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            // Re-submit the current query to force processing
                            if (query.isNotBlank()) {
                                onSubmit?.invoke() ?: onQueryChange(query)
                            }
                        }
                    ),
                    cursorBrush = SolidColor(SearchBarIcon),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = SearchBarIcon,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQueryChange("") }
                )
            }
        }

        AnimatedVisibility(visible = error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        }

        AnimatedVisibility(visible = query.isEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FILTER_HINTS) { (token, _) ->
                    Text(
                        text = token,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onQueryChange(token) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
