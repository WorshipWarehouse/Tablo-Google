package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TvFocusHighlight
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun TvRemoteKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }

    val digitsRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val lettersRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val lettersRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "@")
    val lettersRow3 = listOf("z", "x", "c", "v", "b", "n", "m", ".", "-", "_")

    Column(
        modifier = modifier
            .background(Color(0xE6101726), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Digits
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            digitsRow.forEach { char ->
                TvKeyButton(
                    text = char,
                    onClick = { onKeyPress(char) },
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // QWERTY Row 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lettersRow1.forEach { char ->
                val displayChar = if (isShifted) char.uppercase() else char
                TvKeyButton(
                    text = displayChar,
                    onClick = { onKeyPress(displayChar) },
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // QWERTY Row 2
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lettersRow2.forEach { char ->
                val displayChar = if (isShifted && char.length == 1 && char[0].isLetter()) char.uppercase() else char
                TvKeyButton(
                    text = displayChar,
                    onClick = { onKeyPress(displayChar) },
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // QWERTY Row 3
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lettersRow3.forEach { char ->
                val displayChar = if (isShifted && char.length == 1 && char[0].isLetter()) char.uppercase() else char
                TvKeyButton(
                    text = displayChar,
                    onClick = { onKeyPress(displayChar) },
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // Bottom Action Keys Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TvKeyButton(
                text = if (isShifted) "a-z" else "A-Z",
                onClick = { isShifted = !isShifted },
                modifier = Modifier.weight(1f).height(40.dp)
            )
            TvKeyButton(
                text = "SPACE",
                icon = Icons.Default.SpaceBar,
                onClick = { onKeyPress(" ") },
                modifier = Modifier.weight(1.8f).height(40.dp)
            )
            TvKeyButton(
                text = "DEL",
                icon = Icons.Default.Backspace,
                onClick = onBackspace,
                modifier = Modifier.weight(1.2f).height(40.dp)
            )
            TvKeyButton(
                text = "CLEAR",
                icon = Icons.Default.Clear,
                onClick = onClear,
                modifier = Modifier.weight(1.2f).height(40.dp)
            )
            if (onDone != null) {
                TvKeyButton(
                    text = "ENTER",
                    onClick = onDone,
                    modifier = Modifier.weight(1.4f).height(40.dp)
                )
            }
        }
    }
}

@Composable
fun TvKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        else -> TvSurfaceElevated
    }
    val contentColor = when {
        isFocused -> Color.Black
        else -> TextPrimary
    }
    val border = if (isFocused) {
        BorderStroke(2.dp, TvFocusHighlight)
    } else {
        BorderStroke(1.dp, Color(0x33FFFFFF))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(border, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
