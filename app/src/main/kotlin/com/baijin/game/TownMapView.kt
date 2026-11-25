package com.baijin.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import com.baijin.game.core.Direction
import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.Exploration
import com.baijin.game.core.GameState
import com.baijin.game.core.Gender
import com.baijin.game.core.NpcMarker
import com.baijin.game.core.QuestStage
import com.baijin.game.core.Tile
import com.baijin.game.core.content.SceneDefinition
import kotlin.math.abs
import kotlin.math.sin

internal class TownMapView(context: Context) : View(context) {
    private val art = TownArtwork(context)
    private val home by lazy { HomeArtwork(context) }
    private val life = TownLifeArtwork()
    private val traveler by lazy { TravellerArtwork(context) }
    private val walk = WalkCycle()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = RectF()
    private val exitArrow = Path().apply {
        moveTo(0f, -0.25f)
        lineTo(0.19f, 0.10f)
        lineTo(-0.19f, 0.10f)
        close()
    }
    private val closedExit = Path().apply {
        addRoundRect(-.25f, -.06f, .25f, .039f, .025f, .025f, Path.Direction.CW)
        addRoundRect(-.224f, -.145f, -.148f, .122f, .021f, .021f, Path.Direction.CW)
        addRoundRect(.148f, -.145f, .224f, .122f, .021f, .021f, Path.Direction.CW)
    }
    private var board: PaintedMap? = null
    private var player: Tile? = null
    private var npcMarkers = emptyList<NpcMarker>()
    private var choreMarker: Pair<Tile, String>? = null
    private var facing = Direction.SOUTH
    private var gender = Gender.MALE
    private var equipped = emptyMap<EquipmentSlot, String>()
    private var playerX = 0f
    private var playerY = 0f
    private var lift = 0f
    private var animator: ValueAnimator? = null
    private var ambience: ValueAnimator? = null
    private var ambientPhase = 0f
    private var visibleToUser = false

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun show(state: GameState, exploration: Exploration) {
        val next = state.position.tile
        val previous = player
        val changedScene = board?.scene !== exploration.scene
        if (changedScene) {
            board = paintMap(exploration.scene)
            npcMarkers = exploration.npcMarkers
        }
        val chore = exploration.quests.firstNotNullOfOrNull { quest ->
            val objective = quest.objective
            if (quest.round?.stage == QuestStage.IN_PROGRESS && objective?.locationId != null) {
                exploration.locations.firstOrNull { it.id == objective.locationId }?.tile?.let { it to objective.actionLabel }
            } else null
        }
        if (choreMarker != chore) {
            choreMarker = chore
            invalidate()
        }
        updateAmbience()
        if (equipped != state.equipped) {
            equipped = state.equipped
            invalidate()
        }
        if (!changedScene && previous == next && facing == state.facing && gender == state.character.gender) return

        facing = state.facing
        gender = state.character.gender
        player = next
        contentDescription = context.getString(
            R.string.map_position, exploration.scene.name, exploration.locationName, next.x, next.y,
        )
        val stepped = previous != null && abs(next.x - previous.x) + abs(next.y - previous.y) == 1
        positionPlayer(next, smooth = !changedScene && stepped && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled())
    }

    override fun onDetachedFromWindow() {
        ambience?.cancel()
        ambience = null
        animator?.cancel()
        animator = null
        player?.let { positionPlayer(it, smooth = false) }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleToUser = isVisible
        if (!isVisible) {
            animator?.cancel()
            animator = null
            player?.let { positionPlayer(it, smooth = false) }
        }
        updateAmbience()
    }

    private fun updateAmbience() {
        val animate = visibleToUser && isAttachedToWindow && board?.scene?.indoors == false &&
            ValueAnimator.areAnimatorsEnabled()
        if (!animate) {
            ambience?.cancel()
            ambience = null
            return
        }
        if (ambience != null) return
        ambience = ValueAnimator.ofFloat(0f, 1f).apply {
            var previousFrame = -50L
            duration = 6_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (it.currentPlayTime - previousFrame >= 50L) {
                    ambientPhase = it.animatedValue as Float
                    previousFrame = it.currentPlayTime
                    invalidate()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val map = board ?: return
        val scene = map.scene
        val indoors = scene.indoors
        val playerScale = if (scene.id == HomeArtwork.SCENE_ID) 1.45f else 1f
        val topInset = if (indoors) context.dp(48).coerceAtMost(height / 4) else 0
        val mapHeight = height - topInset
        val cell = if (indoors) {
            minOf(width / (scene.width + 3f), mapHeight / ((scene.height + 1.10f) * DEPTH))
        } else {
            maxOf(context.dp(56).toFloat(), width / (scene.width + 2f), height / (scene.height * DEPTH + 2f))
        }
        val row = cell * DEPTH
        val left = cameraOrigin(playerX, width / cell, scene.width)
        val top = cameraOrigin(playerY, mapHeight / row, scene.height) - topInset / row
        canvas.save()
        canvas.scale(cell, row)
        canvas.translate(-left, -top)

        if (indoors) {
            home.drawRoom(canvas, scene)
        } else {
            canvas.drawPaint(art.yard)
            map.groundCover.forEach { drawSprite(canvas, it) }
            canvas.drawPath(map.paved, art.stone)
            drawWater(canvas, scene, map.water)
            life.water(canvas, scene, ambientPhase)
        }
        scene.exits.forEach { exit ->
            canvas.save()
            canvas.translate(exit.tile.x + 0.5f, exit.tile.y + 0.5f)
            canvas.rotate(when (exit.direction) {
                Direction.NORTH -> 0f
                Direction.EAST -> 90f
                Direction.SOUTH -> 180f
                Direction.WEST -> 270f
            })
            if (!indoors && exit.destinationExitId == null) {
                canvas.translate(0f, -.23f)
                ink.color = CLOSED_EXIT_INK
                canvas.drawPath(closedExit, ink)
            } else {
                ink.color = if (indoors) HOME_EXIT_INK else EXIT_INK
                canvas.drawPath(exitArrow, ink)
            }
            canvas.restore()
        }

        var playerDrawn = false
        map.scenery.forEach { sprite ->
            if (!playerDrawn && playerY <= sprite.depth) {
                drawPlayer(canvas, indoors, playerScale)
                playerDrawn = true
            }
            if (sprite.shadow) {
                ink.color = if (indoors) 0x444A472F else SCENERY_SHADOW
                canvas.drawOval(
                    sprite.centerX - .32f * sprite.width, sprite.feetY - .08f,
                    sprite.centerX + .38f * sprite.width, sprite.feetY + .07f, ink,
                )
            }
            drawSprite(canvas, sprite)
        }
        if (!playerDrawn) drawPlayer(canvas, indoors, playerScale)
        npcMarkers.forEach { marker ->
            ink.color = 0xFF43695C.toInt()
            canvas.drawOval(marker.serviceTile.x + .37f, marker.serviceTile.y + .72f,
                marker.serviceTile.x + .63f, marker.serviceTile.y + .85f, ink)
            drawLabel(canvas, marker.tile.x + .5f, marker.tile.y + .24f, marker.name, cell, row)
        }
        choreMarker?.let { (tile, label) ->
            drawLabel(canvas, tile.x + .5f, tile.y + .9f, label, cell, row)
        }
        canvas.restore()
    }

    private fun positionPlayer(tile: Tile, smooth: Boolean) {
        animator?.cancel()
        val endX = tile.x + 0.5f
        val endY = tile.y + 0.69f
        if (!smooth) {
            walk.rest()
            playerX = endX
            playerY = endY
            lift = 0f
            invalidate()
            return
        }
        val startX = playerX
        val startY = playerY
        walk.begin()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 133
            interpolator = LinearInterpolator()
            addUpdateListener { value ->
                val fraction = value.animatedValue as Float
                walk.advance(fraction)
                playerX = startX + (endX - startX) * fraction
                playerY = startY + (endY - startY) * fraction
                lift = (sin(fraction * Math.PI) * 0.018).toFloat()
                invalidate()
            }
            start()
        }
    }

    private fun paintMap(scene: SceneDefinition): PaintedMap {
        val paved = Path()
        if (!scene.indoors) scene.terrain.forEachIndexed { y, row -> row.forEachIndexed { x, tile ->
            if (tile == 'r') {
                if (scene.id == "town_east" || (scene.id == "town" && x == 5 && y == 8)) {
                    addStreetPaving(paved, scene, x, y)
                } else {
                    paved.addRect(x - .01f, y - .01f, x + 1.01f, y + 1.01f, Path.Direction.CW)
                }
            }
        } }
        return PaintedMap(scene, paved, waterPath(scene), art.groundCover(scene), art.scenery(scene))
    }

    private fun addStreetPaving(paved: Path, scene: SceneDefinition, x: Int, y: Int) {
        fun roadAt(column: Int, row: Int) = (scene.terrain.getOrNull(row)?.getOrNull(column) ?: '.') in "rhv"

        val centerX = x + .5f
        val centerY = y + .5f
        val left = x + .10f
        val right = x + .90f
        val top = y + .14f
        val bottom = y + .86f
        val clockwise = Path.Direction.CW
        paved.addOval(left, top, right, bottom, clockwise)
        if (x == 0 || roadAt(x - 1, y)) paved.addRect(x - .01f, top, centerX, bottom, clockwise)
        val east = roadAt(x + 1, y)
        val south = roadAt(x, y + 1)
        if (east) paved.addRect(centerX, top, x + 1.01f, bottom, clockwise)
        if (roadAt(x, y - 1)) paved.addRect(left, y - .01f, right, centerY, clockwise)
        if (south) paved.addRect(left, centerY, right, y + 1.01f, clockwise)
        if (east && south && roadAt(x + 1, y + 1)) {
            paved.addRect(centerX, centerY, centerX + 1f, centerY + 1f, clockwise)
        }
    }

    private fun waterPath(scene: SceneDefinition): Path {
        fun wet(x: Int, y: Int) = scene.terrain.getOrNull(y)?.getOrNull(x) in listOf('w', 'h', 'v')
        val channel = Path()
        val offsets = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        scene.terrain.forEachIndexed { y, row -> row.forEachIndexed cells@ { x, tile ->
            if (!wet(x, y)) return@cells
            val neighbours = offsets.filter { (dx, dy) ->
                wet(x + dx, y + dy) || !scene.contains(Tile(x + dx, y + dy))
            }
            fun reach(offset: Pair<Int, Int>) =
                if (scene.contains(Tile(x + offset.first, y + offset.second))) .51f else 3f
            val cx = x + .5f; val cy = y + .5f
            if (neighbours.size == 2) {
                val (a, b) = neighbours
                channel.moveTo(cx + a.first * reach(a), cy + a.second * reach(a))
                channel.quadTo(cx, cy, cx + b.first * reach(b), cy + b.second * reach(b))
            } else if (neighbours.isEmpty()) {
                channel.addCircle(cx, cy, .16f, Path.Direction.CW)
            } else neighbours.forEach { (dx, dy) ->
                val distance = reach(dx to dy)
                channel.moveTo(cx, cy); channel.lineTo(cx + dx * distance, cy + dy * distance)
            }
        } }
        return channel
    }

    private fun drawWater(canvas: Canvas, scene: SceneDefinition, channel: Path) {
        ink.style = Paint.Style.STROKE
        ink.strokeJoin = Paint.Join.ROUND
        ink.strokeCap = Paint.Cap.ROUND
        ink.color = 0xFF9B9B78.toInt(); ink.strokeWidth = .66f
        canvas.drawPath(channel, ink)
        ink.color = 0xFF6C8580.toInt(); ink.strokeWidth = .48f
        canvas.drawPath(channel, ink)
        ink.color = 0xFFA6C1B7.toInt(); ink.strokeWidth = .39f
        canvas.drawPath(channel, ink)
        ink.style = Paint.Style.FILL
        scene.terrain.forEachIndexed { y, row -> row.forEachIndexed bridge@ { x, tile ->
            if (tile == 'h' || tile == 'v') {
                if (scene.id == "town" && x == 7 && y == 6) {
                    drawHomeBridge(canvas, x, y)
                    return@bridge
                }
                val horizontal = tile == 'h'
                val left = x + if (horizontal) -.08f else .14f
                val top = y + if (horizontal) .14f else -.08f
                val right = x + if (horizontal) 1.08f else .86f
                val bottom = y + if (horizontal) .86f else 1.08f
                canvas.drawRect(left, top, right, bottom, art.stone)
                ink.style = Paint.Style.STROKE; ink.strokeWidth = .055f; ink.color = 0xFF776F5C.toInt()
                if (horizontal) {
                    canvas.drawLine(left, top, right, top, ink); canvas.drawLine(left, bottom, right, bottom, ink)
                } else {
                    canvas.drawLine(left, top, left, bottom, ink); canvas.drawLine(right, top, right, bottom, ink)
                }
                ink.style = Paint.Style.FILL
            }
        } }
    }

    private fun drawHomeBridge(canvas: Canvas, x: Int, y: Int) {
        val left = x + .11f
        val right = x + .89f
        val top = y - .17f
        val bottom = y + 1.17f
        ink.color = 0x40574C39
        canvas.drawRect(left - .035f, top, right + .035f, bottom + .08f, ink)
        canvas.drawRect(left, top, right, bottom, art.stone)
        ink.color = 0xFF867760.toInt()
        canvas.drawRect(left, top, right, top + .065f, ink)
        canvas.drawRect(left, bottom - .065f, right, bottom, ink)
        ink.color = 0xFF67513B.toInt()
        canvas.drawRoundRect(left - .029f, top + .07f, left + .029f, bottom - .07f, .022f, .022f, ink)
        canvas.drawRoundRect(right - .029f, top + .07f, right + .029f, bottom - .07f, .022f, .022f, ink)
        for (bank in listOf(top + .11f, bottom - .11f)) {
            for (rail in listOf(left, right)) {
                ink.color = 0xFF5B4A37.toInt()
                canvas.drawRoundRect(rail - .06f, bank - .08f, rail + .06f, bank + .048f, .02f, .02f, ink)
                ink.color = 0xFFC1A77F.toInt()
                canvas.drawRoundRect(rail - .055f, bank - .08f, rail + .055f, bank - .034f, .016f, .016f, ink)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas, indoors: Boolean, scale: Float) {
        val x = playerX
        val y = playerY
        ink.color = PLAYER_SHADOW
        canvas.drawOval(x - 0.18f * scale, y - 0.058f * scale, x + 0.18f * scale, y + 0.065f * scale, ink)
        ink.color = if (indoors) HOME_PLAYER_OUTLINE else PLAYER_OUTLINE
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = (if (indoors) 0.014f else 0.027f) * scale
        canvas.drawOval(x - 0.19f * scale, y - 0.063f * scale, x + 0.19f * scale, y + 0.071f * scale, ink)
        ink.style = Paint.Style.FILL
        traveler.draw(canvas, gender, facing, walk.frame, x, y - lift, PLAYER_HEIGHT * scale, DEPTH, bitmapPaint, equipped)
    }

    private fun drawSprite(canvas: Canvas, sprite: TownArtwork.Sprite) {
        val width = sprite.width
        val height = sprite.height ?: sprite.image?.let { width * it.height / it.width / DEPTH } ?: 1f
        destination.set(sprite.centerX - width / 2f, sprite.feetY - height, sprite.centerX + width / 2f, sprite.feetY)
        val posed = life.pose(canvas, sprite, ambientPhase)
        sprite.image?.let { canvas.drawBitmap(it, null, destination, bitmapPaint) }
        life.draw(canvas, sprite, height, ambientPhase)
        if (posed) canvas.restore()
    }

    private fun drawLabel(canvas: Canvas, x: Float, y: Float, label: String, cell: Float, row: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1f / cell, 1f / row)
        ink.textAlign = Paint.Align.CENTER
        ink.textSize = context.dp(12).toFloat()
        ink.style = Paint.Style.STROKE; ink.strokeWidth = context.dp(3).toFloat()
        ink.color = 0xFFF1E4CD.toInt(); canvas.drawText(label, 0f, 0f, ink)
        ink.style = Paint.Style.FILL; ink.color = 0xFF344945.toInt(); canvas.drawText(label, 0f, 0f, ink)
        canvas.restore()
    }

    private fun cameraOrigin(center: Float, visible: Float, mapSize: Int): Float =
        if (visible >= mapSize) (mapSize - visible) / 2f else (center - visible / 2f).coerceIn(0f, mapSize - visible)

    private data class PaintedMap(
        val scene: SceneDefinition,
        val paved: Path,
        val water: Path,
        val groundCover: List<TownArtwork.Sprite>,
        val scenery: List<TownArtwork.Sprite>,
    )

    private companion object {
        const val DEPTH = 0.77f
        // Match the height of ordinary town doors in screen space.
        const val PLAYER_HEIGHT = 0.70f
        val EXIT_INK = 0xE63F6057.toInt()
        val CLOSED_EXIT_INK = 0xFF725C46.toInt()
        val HOME_EXIT_INK = 0xFFEAD9AA.toInt()
        val SCENERY_SHADOW = 0x183E4636
        val PLAYER_SHADOW = 0x80605B49.toInt()
        val PLAYER_OUTLINE = 0xFAE9F2D5.toInt()
        val HOME_PLAYER_OUTLINE = 0xC4F5E4BA.toInt()
    }
}
