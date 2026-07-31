package com.example.futureconflicts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.futureconflicts.game.Battle
import com.example.futureconflicts.game.Pos
import com.example.futureconflicts.game.Team
import com.example.futureconflicts.game.Terrain
import com.example.futureconflicts.game.Unit
import kotlin.math.min

private object Palette {
    val screenBg = Color(0xFF0E1116)
    val gridLine = Color(0x33000000)
    val plains = Color(0xFF6E9A3E)
    val road = Color(0xFFC2B189)
    val forest = Color(0xFF33612C)
    val mountain = Color(0xFF8A7355)
    val city = Color(0xFF9AA3AD)
    val hq = Color(0xFFCBA135)
    val sea = Color(0xFF2C5C86)
    val moveTint = Color(0x553D7DD8)
    val targetTint = Color(0x66E5484D)
    val selectRing = Color(0xFFFFFFFF)
    val player = Color(0xFF3D7DD8)
    val enemy = Color(0xFFE5484D)
    val hpGood = Color(0xFFFFFFFF)
    val hpLow = Color(0xFFFFD54F)
    val hud = Color(0xFFE6ECF2)
    val hudDim = Color(0xFF9AA6B2)
}

private fun terrainColor(t: Terrain): Color = when (t) {
    Terrain.PLAINS -> Palette.plains
    Terrain.ROAD -> Palette.road
    Terrain.FOREST -> Palette.forest
    Terrain.MOUNTAIN -> Palette.mountain
    Terrain.CITY -> Palette.city
    Terrain.HQ -> Palette.hq
    Terrain.SEA -> Palette.sea
}

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val battle = remember { Battle() }
    val textMeasurer = rememberTextMeasurer()

    // Bumped after every interaction so the Canvas recomposes; the Battle itself
    // is mutated in place.
    var version by remember { mutableStateOf(0) }

    // Board geometry cached from the last draw, for mapping taps -> cells.
    var cellSize by remember { mutableStateOf(0f) }
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxSize().background(Palette.screenBg).systemBarsPadding()) {
        @Suppress("UNUSED_EXPRESSION") version // recompose HUD on state change

        Text(
            text = "FUTURE CONFLICTS",
            color = Palette.hud,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp),
        )
        Text(
            text = "Day ${battle.day}  ·  ${battle.turn.label}'s turn",
            color = Palette.hudDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
        Text(
            text = battle.message,
            color = Palette.hud,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (cellSize <= 0f) return@detectTapGestures
                            val gx = ((offset.x - originX) / cellSize).toInt()
                            val gy = ((offset.y - originY) / cellSize).toInt()
                            battle.onTap(Pos(gx, gy))
                            version++
                        }
                    },
            ) {
                @Suppress("UNUSED_EXPRESSION") version // redraw dependency

                val cs = min(size.width / battle.map.cols, size.height / battle.map.rows)
                val ox = (size.width - cs * battle.map.cols) / 2f
                val oy = (size.height - cs * battle.map.rows) / 2f
                cellSize = cs; originX = ox; originY = oy

                drawBoard(battle, cs, ox, oy)
                drawUnits(battle, cs, ox, oy, textMeasurer)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { battle.endPlayerTurn(); version++ },
                enabled = battle.winner == null && battle.turn == Team.PLAYER,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.player),
                modifier = Modifier.weight(1f),
            ) { Text("End Turn") }

            OutlinedButton(
                onClick = { battle.restart(); version++ },
                modifier = Modifier.weight(1f),
            ) { Text("Restart") }
        }
    }
}

private fun DrawScope.drawBoard(battle: Battle, cs: Float, ox: Float, oy: Float) {
    for (y in 0 until battle.map.rows) {
        for (x in 0 until battle.map.cols) {
            val tl = Offset(ox + x * cs, oy + y * cs)
            drawRect(color = terrainColor(battle.map[x, y]), topLeft = tl, size = Size(cs, cs))
            drawRect(color = Palette.gridLine, topLeft = tl, size = Size(cs, cs), style = Stroke(1f))
        }
    }
    // Movement range overlay.
    for (p in battle.reachable.keys) {
        drawRect(Palette.moveTint, Offset(ox + p.x * cs, oy + p.y * cs), Size(cs, cs))
    }
    // Attack target overlay.
    for (p in battle.targets) {
        drawRect(Palette.targetTint, Offset(ox + p.x * cs, oy + p.y * cs), Size(cs, cs))
    }
    // Selected-unit ring.
    battle.selected?.let { u ->
        drawRect(
            color = Palette.selectRing,
            topLeft = Offset(ox + u.pos.x * cs, oy + u.pos.y * cs),
            size = Size(cs, cs),
            style = Stroke(width = cs * 0.06f),
        )
    }
}

private fun DrawScope.drawUnits(
    battle: Battle,
    cs: Float,
    ox: Float,
    oy: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    for (u in battle.units) {
        if (!u.alive) continue
        val body = if (u.team == Team.PLAYER) Palette.player else Palette.enemy
        val faded = if (u.hasActed && u.team == Team.PLAYER) body.copy(alpha = 0.45f) else body
        val inset = cs * 0.14f
        val tl = Offset(ox + u.pos.x * cs + inset, oy + u.pos.y * cs + inset)
        val sz = Size(cs - 2 * inset, cs - 2 * inset)
        drawRoundRect(
            color = faded,
            topLeft = tl,
            size = sz,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cs * 0.12f, cs * 0.12f),
        )

        // Unit type glyph, centered.
        val glyph = textMeasurer.measure(
            AnnotatedString(u.type.glyph),
            style = TextStyle(color = Color.White, fontSize = (cs * 0.42f).toSp(), fontWeight = FontWeight.Bold),
        )
        drawText(
            glyph,
            topLeft = Offset(
                ox + u.pos.x * cs + (cs - glyph.size.width) / 2f,
                oy + u.pos.y * cs + (cs - glyph.size.height) / 2f,
            ),
        )

        // HP number, bottom-right, only when damaged.
        if (u.hp < Unit.MAX_HP) {
            val hpColor = if (u.hp <= 3) Palette.hpLow else Palette.hpGood
            val hp = textMeasurer.measure(
                AnnotatedString(u.hp.toString()),
                style = TextStyle(color = hpColor, fontSize = (cs * 0.30f).toSp(), fontWeight = FontWeight.Bold),
            )
            drawText(
                hp,
                topLeft = Offset(
                    ox + (u.pos.x + 1) * cs - hp.size.width - cs * 0.06f,
                    oy + (u.pos.y + 1) * cs - hp.size.height - cs * 0.02f,
                ),
            )
        }
    }
}
