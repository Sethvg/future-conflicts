package com.example.futureconflicts.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.example.futureconflicts.game.Terrain
import com.example.futureconflicts.game.UnitType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decode encoded PNG [bytes] into an [ImageBitmap]. Platform-specific: Android uses
 * `BitmapFactory`, the skiko targets (jvm host tests + iOS) use `org.jetbrains.skia`.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

/**
 * The decoded Batch-1 sprites: neutral-gray unit tokens (team-tinted at draw time)
 * and terrain tiles. Anything missing/undecodable is simply absent — the renderer
 * falls back to primitives, so the game still draws if a sprite is unavailable.
 */
class SpriteSet(
    val units: Map<UnitType, ImageBitmap>,
    val terrain: Map<Terrain, ImageBitmap>,
)

/** Decode the embedded [SpriteData] once. Call inside `remember` so it runs a single time. */
@OptIn(ExperimentalEncodingApi::class)
fun loadSprites(): SpriteSet {
    fun decode(b64: String): ImageBitmap? =
        runCatching { decodeImageBitmap(Base64.decode(b64)) }.getOrNull()

    val units = SpriteData.unit.mapNotNull { (name, b64) ->
        val type = runCatching { UnitType.valueOf(name) }.getOrNull() ?: return@mapNotNull null
        decode(b64)?.let { type to it }
    }.toMap()

    val terrain = SpriteData.terrain.mapNotNull { (name, b64) ->
        val t = runCatching { Terrain.valueOf(name) }.getOrNull() ?: return@mapNotNull null
        decode(b64)?.let { t to it }
    }.toMap()

    return SpriteSet(units, terrain)
}
