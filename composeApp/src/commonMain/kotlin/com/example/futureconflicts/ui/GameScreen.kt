package com.example.futureconflicts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.futureconflicts.game.Battle
import com.example.futureconflicts.game.Building
import com.example.futureconflicts.game.Commanders
import com.example.futureconflicts.game.Economy
import com.example.futureconflicts.game.Pos
import com.example.futureconflicts.game.Team
import com.example.futureconflicts.game.Terrain
import com.example.futureconflicts.game.Unit
import com.example.futureconflicts.game.UnitType
import kotlin.math.min
import kotlin.math.roundToInt

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
    // Lighter team colors used to *multiply* (Modulate) the neutral-gray unit sprites;
    // the UI blue/red above are too dark for that and murk the art.
    val playerTint = Color(0xFF9DC2FF)
    val enemyTint = Color(0xFFFF9A94)
    val neutral = Color(0xFFB6BEC7)
    val capture = Color(0xFFFFD54F)
    val fog = Color(0xAA0A0D12)
    val hud = Color(0xFFE6ECF2)
    val hudDim = Color(0xFF9AA6B2)
    val gold = Color(0xFFFFCB2E)
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

private fun ownerColor(team: Team?): Color = when (team) {
    Team.PLAYER -> Palette.player
    Team.ENEMY -> Palette.enemy
    null -> Palette.neutral
}

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val battle = remember { Battle() }
    val textMeasurer = rememberTextMeasurer()
    val sprites = remember { loadSprites() }

    // Bumped after every interaction so the Canvas + HUD recompose; the Battle
    // itself is mutated in place.
    var version by remember { mutableStateOf(0) }
    fun act(block: () -> kotlin.Unit) { block(); version++ }

    var cellSize by remember { mutableStateOf(0f) }
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxSize().background(Palette.screenBg).systemBarsPadding()) {
        @Suppress("UNUSED_EXPRESSION") version

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("FUTURE CONFLICTS", color = Palette.hud, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                val cmd = battle.commanderOf(Team.PLAYER)
                Text(
                    "Day ${battle.day} · ${battle.turn.label}" + (cmd?.let { " · ${it.name}" } ?: ""),
                    color = Palette.hudDim,
                    fontSize = 12.sp,
                )
            }
            Text("◆ ${battle.goldOf(Team.PLAYER)}g", color = Palette.gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Text(
            text = battle.message,
            color = Palette.hud,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (cellSize <= 0f || battle.needsCommanderChoice) return@detectTapGestures
                            val gx = ((offset.x - originX) / cellSize).toInt()
                            val gy = ((offset.y - originY) / cellSize).toInt()
                            act { battle.onTap(Pos(gx, gy)) }
                        }
                    },
            ) {
                @Suppress("UNUSED_EXPRESSION") version

                val cs = min(size.width / battle.map.cols, size.height / battle.map.rows)
                val ox = (size.width - cs * battle.map.cols) / 2f
                val oy = (size.height - cs * battle.map.rows) / 2f
                cellSize = cs; originX = ox; originY = oy

                val visible = battle.visibleTiles(Team.PLAYER)
                drawBoard(battle, sprites, cs, ox, oy)
                drawBuildings(battle, cs, ox, oy, textMeasurer)
                drawFog(battle, visible, cs, ox, oy)
                drawUnits(battle, sprites, cs, ox, oy, textMeasurer, visible)
            }
        }

        Controls(battle, version) { block -> act(block) }
    }
}

@Composable
private fun Controls(battle: Battle, version: Int, act: (() -> kotlin.Unit) -> kotlin.Unit) {
    @Suppress("UNUSED_EXPRESSION") version // recompose the controls whenever state changes
    val pad = Modifier.fillMaxWidth().padding(16.dp)
    when {
        battle.needsCommanderChoice -> Column(
            pad.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose your Commander", color = Palette.hud, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            for (c in Commanders.all) {
                Button(
                    onClick = { act { battle.chooseCommander(c.id) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.player),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        Text(c.theme, fontSize = 11.sp)
                        Text(c.passives.joinToString("  ·  ") { it.label }, fontSize = 11.sp)
                    }
                }
            }
        }

        battle.buildMenuOpen -> Column(
            pad.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Build at HQ — ${battle.goldOf(Team.PLAYER)}g", color = Palette.hud, fontWeight = FontWeight.SemiBold)
            val gold = battle.goldOf(Team.PLAYER)
            for (type in UnitType.entries.filter { it.basic }) {
                val cost = battle.buildCost(Team.PLAYER, type, elite = false) ?: continue
                BuildButton("${type.label} — ${cost}g", enabled = gold >= cost) {
                    act { battle.buildUnit(type) }
                }
            }
            // Elite signature unit.
            battle.commanderOf(Team.PLAYER)?.signature?.let { sig ->
                val cost = battle.buildCost(Team.PLAYER, sig, elite = true)
                if (cost != null) BuildButton("★ Elite ${sig.label} — ${cost}g", enabled = gold >= cost, elite = true) {
                    act { battle.buildUnit(sig, elite = true) }
                }
            }
            // Commander hero unit.
            val cCost = battle.commanderPrice(Team.PLAYER)
            val has = battle.hasCommander(Team.PLAYER)
            BuildButton(
                if (has) "Commander (deployed)" else "★ Commander — ${cCost}g",
                enabled = !has && gold >= cCost,
                elite = true,
            ) { act { battle.buildUnit(UnitType.COMMANDER) } }

            OutlinedButton(onClick = { act { battle.dismissMenus() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }

        battle.upgradeOpen -> Row(pad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val city = battle.upgradeableCity()
            val canAfford = city != null && battle.goldOf(Team.PLAYER) >= Economy.CITY_UPGRADE_COST
            Button(
                onClick = { act { battle.upgradeCity() } },
                enabled = canAfford,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.player),
                modifier = Modifier.weight(1f),
            ) { Text(if (city == null) "Maxed" else "Upgrade (${Economy.CITY_UPGRADE_COST}g)") }
            OutlinedButton(onClick = { act { battle.dismissMenus() } }, modifier = Modifier.weight(1f)) {
                Text("Close")
            }
        }

        battle.phase == Battle.Phase.ACTION -> Row(pad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (battle.canCaptureHere) {
                Button(
                    onClick = { act { battle.captureHere() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.capture),
                    modifier = Modifier.weight(1f),
                ) { Text("Capture", color = Color.Black) }
            }
            Button(
                onClick = { act { battle.waitHere() } },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.player),
                modifier = Modifier.weight(1f),
            ) { Text("Wait") }
            OutlinedButton(onClick = { act { battle.cancelAction() } }, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
        }

        else -> Row(pad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { act { battle.endPlayerTurn() } },
                enabled = battle.winner == null && battle.turn == Team.PLAYER,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.player),
                modifier = Modifier.weight(1f),
            ) { Text("End Turn") }
            OutlinedButton(onClick = { act { battle.restart() } }, modifier = Modifier.weight(1f)) {
                Text("Restart")
            }
        }
    }
}

@Composable
private fun BuildButton(label: String, enabled: Boolean, elite: Boolean = false, onClick: () -> kotlin.Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = if (elite) Palette.capture else Palette.player),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label, color = if (elite) Color.Black else Color.White) }
}

private fun DrawScope.drawBoard(battle: Battle, sprites: SpriteSet, cs: Float, ox: Float, oy: Float) {
    for (y in 0 until battle.map.rows) {
        for (x in 0 until battle.map.cols) {
            val tl = Offset(ox + x * cs, oy + y * cs)
            val tile = battle.map[x, y]
            val img = sprites.terrain[tile]
            if (img != null) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset((ox + x * cs).roundToInt(), (oy + y * cs).roundToInt()),
                    dstSize = IntSize(cs.roundToInt(), cs.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            } else {
                drawRect(color = terrainColor(tile), topLeft = tl, size = Size(cs, cs))
            }
            drawRect(color = Palette.gridLine, topLeft = tl, size = Size(cs, cs), style = Stroke(1f))
        }
    }
    for (p in battle.reachable.keys) {
        drawRect(Palette.moveTint, Offset(ox + p.x * cs, oy + p.y * cs), Size(cs, cs))
    }
    for (p in battle.targets) {
        drawRect(Palette.targetTint, Offset(ox + p.x * cs, oy + p.y * cs), Size(cs, cs))
    }
    // Preview / selection ring.
    (battle.previewPos ?: battle.selected?.pos)?.let { p ->
        drawRect(
            color = Palette.selectRing,
            topLeft = Offset(ox + p.x * cs, oy + p.y * cs),
            size = Size(cs, cs),
            style = Stroke(width = cs * 0.06f),
        )
    }
}

private fun DrawScope.drawBuildings(battle: Battle, cs: Float, ox: Float, oy: Float, tm: TextMeasurer) {
    for (b in battle.buildingsView) {
        val tl = Offset(ox + b.pos.x * cs, oy + b.pos.y * cs)
        // Ownership border.
        drawRect(
            color = ownerColor(b.owner),
            topLeft = tl + Offset(cs * 0.06f, cs * 0.06f),
            size = Size(cs * 0.88f, cs * 0.88f),
            style = Stroke(width = cs * 0.08f),
        )
        // Label: "HQ" or city level.
        val label = if (b.kind == Building.Kind.HQ) "HQ" else "L${b.level}"
        val layout = tm.measure(
            AnnotatedString(label),
            style = TextStyle(color = Color.Black, fontSize = (cs * 0.22f).toSp(), fontWeight = FontWeight.Bold),
        )
        drawText(layout, topLeft = Offset(ox + b.pos.x * cs + cs * 0.08f, oy + b.pos.y * cs + cs * 0.05f))
        // Capture progress.
        if (b.captureLeft < Economy.CAPTURE_POINTS) {
            val frac = 1f - b.captureLeft.toFloat() / Economy.CAPTURE_POINTS
            drawRect(
                color = Palette.capture,
                topLeft = Offset(ox + b.pos.x * cs, oy + (b.pos.y + 1) * cs - cs * 0.10f),
                size = Size(cs * frac, cs * 0.10f),
            )
        }
    }
}

private fun DrawScope.drawFog(battle: Battle, visible: Set<Pos>, cs: Float, ox: Float, oy: Float) {
    for (y in 0 until battle.map.rows) {
        for (x in 0 until battle.map.cols) {
            if (Pos(x, y) !in visible) {
                drawRect(Palette.fog, Offset(ox + x * cs, oy + y * cs), Size(cs, cs))
            }
        }
    }
}

private fun DrawScope.drawUnits(
    battle: Battle,
    sprites: SpriteSet,
    cs: Float,
    ox: Float,
    oy: Float,
    tm: TextMeasurer,
    visible: Set<Pos>,
) {
    for (u in battle.units) {
        if (!u.alive) continue
        if (!battle.isUnitVisible(Team.PLAYER, u, visible)) continue // hidden by fog
        // Draw the selected unit at its previewed destination while choosing an action.
        val drawPos = if (u === battle.selected && battle.phase == Battle.Phase.ACTION) {
            battle.previewPos ?: u.pos
        } else u.pos

        val teamColor = if (u.team == Team.PLAYER) Palette.player else Palette.enemy
        val tintColor = if (u.team == Team.PLAYER) Palette.playerTint else Palette.enemyTint
        val dim = u.hasActed && u.team == Team.PLAYER
        val inset = cs * 0.14f
        val tl = Offset(ox + drawPos.x * cs + inset, oy + drawPos.y * cs + inset)
        val sz = Size(cs - 2 * inset, cs - 2 * inset)

        val img = sprites.units[u.type]
        if (img != null) {
            // Neutral-gray sprite multiplied by a light team color (Modulate) so the
            // detail survives while the unit still reads clearly as blue vs red.
            drawImage(
                image = img,
                dstOffset = IntOffset((ox + drawPos.x * cs).roundToInt(), (oy + drawPos.y * cs).roundToInt()),
                dstSize = IntSize(cs.roundToInt(), cs.roundToInt()),
                alpha = if (dim) 0.55f else 1f,
                colorFilter = ColorFilter.tint(tintColor, BlendMode.Modulate),
                filterQuality = FilterQuality.None,
            )
        } else {
            // Fallback if a sprite is missing: the original colored token + glyph.
            val faded = if (dim) teamColor.copy(alpha = 0.45f) else teamColor
            drawRoundRect(color = faded, topLeft = tl, size = sz, cornerRadius = CornerRadius(cs * 0.12f, cs * 0.12f))
            val glyph = tm.measure(
                AnnotatedString(u.type.glyph),
                style = TextStyle(color = Color.White, fontSize = (cs * 0.40f).toSp(), fontWeight = FontWeight.Bold),
            )
            drawText(
                glyph,
                topLeft = Offset(
                    ox + drawPos.x * cs + (cs - glyph.size.width) / 2f,
                    oy + drawPos.y * cs + (cs - glyph.size.height) / 2f,
                ),
            )
        }
        if (u.elite) {
            drawRoundRect(
                color = Palette.capture,
                topLeft = tl,
                size = sz,
                cornerRadius = CornerRadius(cs * 0.12f, cs * 0.12f),
                style = Stroke(width = cs * 0.05f),
            )
        }

        if (u.hp < Unit.MAX_HP) {
            val hpColor = if (u.hp <= 3) Palette.capture else Color.White
            val hp = tm.measure(
                AnnotatedString(u.hp.toString()),
                style = TextStyle(color = hpColor, fontSize = (cs * 0.30f).toSp(), fontWeight = FontWeight.Bold),
            )
            drawText(
                hp,
                topLeft = Offset(
                    ox + (drawPos.x + 1) * cs - hp.size.width - cs * 0.06f,
                    oy + (drawPos.y + 1) * cs - hp.size.height - cs * 0.02f,
                ),
            )
        }
    }
}
