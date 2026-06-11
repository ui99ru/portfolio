package com.tinvestlite.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/**
 * Round instrument logo loaded from the T-Invest brand CDN. While loading or on
 * error it shows a colored monogram (first letter of the ticker), so the list
 * never renders empty gaps.
 */
@Composable
fun InstrumentIcon(
    logoUrl: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val monogram = fallbackText.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        var showMonogram by remember(logoUrl) { mutableStateOf(logoUrl.isNullOrBlank()) }

        if (!logoUrl.isNullOrBlank()) {
            val painter = rememberAsyncImagePainter(
                model = logoUrl,
                onState = { state ->
                    showMonogram = state is AsyncImagePainter.State.Error ||
                        state is AsyncImagePainter.State.Empty
                },
            )
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size.dp).clip(CircleShape),
            )
        }

        if (showMonogram) {
            Text(
                text = monogram,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size / 2.4).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
