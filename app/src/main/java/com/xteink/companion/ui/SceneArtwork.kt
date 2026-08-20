package com.xteink.companion.ui

import androidx.annotation.DrawableRes
import com.xteink.companion.R

data class SceneArtwork(
    @param:DrawableRes val phonePreview: Int,
    @param:DrawableRes val x3PayloadCrop: Int,
)

fun sceneArtworkFor(mode: CompanionColorMode): SceneArtwork = when (mode) {
    CompanionColorMode.Light -> SceneArtwork(
        phonePreview = R.drawable.art_lighthouse_full,
        x3PayloadCrop = R.drawable.x3_lighthouse_crop,
    )
    CompanionColorMode.Dark -> SceneArtwork(
        phonePreview = R.drawable.art_astronaut_landscape,
        x3PayloadCrop = R.drawable.x3_astronaut_crop,
    )
}
