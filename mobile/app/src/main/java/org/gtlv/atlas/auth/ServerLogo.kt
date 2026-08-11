package org.gtlv.atlas.auth

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.gtlv.atlas.R

@Composable
fun ServerLogo(
    serverAddress: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val fallbackPainter =
        painterResource(R.drawable.ic_atlas_fallback)

    val logoUrl = remember(serverAddress) {
        serverAddress
            .trim()
            .removeSuffix("/")
            .takeIf { it.isNotBlank() }
            ?.let { "$it/api/config/logo" }
    }

    val request = remember(logoUrl) {
        ImageRequest.Builder(context)
            .data(logoUrl)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        fallback = fallbackPainter,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(88.dp)
    )
}