package com.messark.hawker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fillColor: Color = Color.White,
    outlineColor: Color = Color.Black,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val outlineOffset = 1.dp
        Text(
            text = text,
            color = outlineColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            maxLines = maxLines,
            style = style,
            modifier = Modifier.offset(x = -outlineOffset, y = -outlineOffset)
        )
        Text(
            text = text,
            color = outlineColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            maxLines = maxLines,
            style = style,
            modifier = Modifier.offset(x = outlineOffset, y = -outlineOffset)
        )
        Text(
            text = text,
            color = outlineColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            maxLines = maxLines,
            style = style,
            modifier = Modifier.offset(x = -outlineOffset, y = outlineOffset)
        )
        Text(
            text = text,
            color = outlineColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            maxLines = maxLines,
            style = style,
            modifier = Modifier.offset(x = outlineOffset, y = outlineOffset)
        )
        Text(
            text = text,
            color = fillColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            maxLines = maxLines,
            style = style
        )
    }
}
