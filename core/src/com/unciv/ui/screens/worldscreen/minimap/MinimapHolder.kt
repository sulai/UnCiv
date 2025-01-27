package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.addInTable
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder

private const val fl = 150f

class MinimapHolder(val mapHolder: WorldMapHolder) : Table() {
    private val worldScreen = mapHolder.worldScreen
    private var minimapSize = Int.MIN_VALUE
    private var maximized = false
    lateinit var minimap: Minimap

    /** Button, next to the minimap, to toggle the unit movement map overlay. */
    val movementsImageButton = MapOverlayToggleButton(
        "TileIcons/MapOverlayToggleMovement",
        getter = { UncivGame.Current.settings.showUnitMovements },
        setter = { UncivGame.Current.settings.showUnitMovements = it }
    )
    /** Button, next to the minimap, to toggle the tile yield map overlay. */
    val yieldImageButton = MapOverlayToggleButton(
        "TileIcons/MapOverlayToggleYields",
        // This is a use in the UI that has little to do with the stat… These buttons have more in common with each other than they do with other uses of getStatIcon().
        getter = { UncivGame.Current.settings.showTileYields },
        setter = { UncivGame.Current.settings.showTileYields = it }
    )
    /** Button, next to the minimap, to toggle the worked tiles map overlay. */
    val populationImageButton = MapOverlayToggleButton(
        "TileIcons/MapOverlayToggleWorkedTiles",
        getter = { UncivGame.Current.settings.showWorkedTiles },
        setter = { UncivGame.Current.settings.showWorkedTiles = it }
    )
    /** Button, next to the minimap, to toggle the resource icons map overlay. */
    val resourceImageButton = MapOverlayToggleButton(
        "TileIcons/MapOverlayToggleResources",
        getter = { UncivGame.Current.settings.showResourcesAndImprovements },
        setter = { UncivGame.Current.settings.showResourcesAndImprovements = it }
    )

    private fun rebuildIfSizeChanged(civInfo: Civilization) {
        // For Spectator should not restrict minimap
        val civ: Civilization? = civInfo.takeUnless { GUI.getViewingPlayer().isSpectator() }
        val newMinimapSize = worldScreen.game.settings.minimapSize
        if (newMinimapSize == minimapSize && civ?.exploredRegion?.shouldUpdateMinimap() != true) return
        minimapSize = newMinimapSize
        rebuild(civ)
    }

    private fun rebuild(civInfo: Civilization?) {
        this.clear()
        minimap = Minimap(mapHolder, minimapSize, civInfo)
        val wrappedMinimap = getWrappedMinimap()
        add(getToggleIcons(wrappedMinimap.height)).bottom().padRight(5f)
        
        val stack = Stack()
        stack.add(wrappedMinimap)
        stack.addInTable(getCornerHandleIcon()).size(30f).pad(10f).top().left()
        stack.addInTable(getMaximizeToggleButton(civInfo)).size(30f).pad(10f).bottom().right()
        add(stack).bottom()
        
        pack()
        if (stage != null) x = stage.width - width
        
        addListener(ResizeDragListener(civInfo))
    }

    private fun rebuildAndUpdateMap(civInfo: Civilization?) {
        rebuild(civInfo) // re-create views
        civInfo?.let { minimap.update(it) } // update map
        minimap.mapHolder.onViewportChanged() // update scroll position
    }

    private fun getMaximizeToggleButton(civInfo: Civilization?): Image {
        // when maximized, collapse map when a location was clicked
        if (maximized) {
            minimap.onClick { maximized = false }
        }
        val name = if(maximized) "Reduce" else "Increase"
        return ImageGetter.getImage("OtherIcons/$name").apply {
            // if the minimap is very small, hide icon to keep vision on map,
            // but keep it functional when clicking on it
            color.a = if(minimap.width > 150f || minimap.height > 150f) 1f else 0f
            onActivation { 
                maximized = !maximized
                minimapSize = if (maximized) {
                    minimap.getClosestMinimapSize(Vector2(stage.width, stage.height)) - 2
                } else {
                    worldScreen.game.settings.minimapSize
                }
                rebuildAndUpdateMap(civInfo)
            }
        }
    }

    private fun getCornerHandleIcon(): Image {
        return ImageGetter.getImage("OtherIcons/Corner").apply {
            touchable = Touchable.disabled
            isVisible = !maximized && (minimap.width > 150f || minimap.height > 150f)
        }
    }

    private fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()
        internalMinimapWrapper.add(minimap)

        internalMinimapWrapper.background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/Minimap/Background",
            tintColor = Color.GRAY
        )
        internalMinimapWrapper.pack()

        val externalMinimapWrapper = Table()
        externalMinimapWrapper.add(internalMinimapWrapper).pad(5f)
        externalMinimapWrapper.background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/Minimap/Border",
            tintColor = Color.WHITE
        )
        externalMinimapWrapper.pack()

        return externalMinimapWrapper
    }

    /** @return Layout table for the little green map overlay toggle buttons, show to the left of the minimap. */
    private fun getToggleIcons(minimapHeight: Float): Table {
        val toggleIconTable = Table()
        
        val availableForPadding = minimapHeight - (movementsImageButton.height + yieldImageButton.height + 
                populationImageButton.height + resourceImageButton.height)
        val paddingBetweenElements = (availableForPadding/3).coerceIn(0f, 5f)
        
        toggleIconTable.defaults().padTop(paddingBetweenElements)

        toggleIconTable.add(movementsImageButton).row()
        toggleIconTable.add(yieldImageButton).row()
        toggleIconTable.add(populationImageButton).row()
        toggleIconTable.add(resourceImageButton).row()

        return toggleIconTable
    }

    fun update(civInfo: Civilization) {
        rebuildIfSizeChanged(civInfo)
        isVisible = UncivGame.Current.settings.showMinimap
        if (isVisible) {
            minimap.update(civInfo)
            movementsImageButton.update()
            yieldImageButton.update()
            populationImageButton.update()
            resourceImageButton.update()
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = super.hit(x, y, touchable)
    override fun act(delta: Float){} // No actions

    inner class ResizeDragListener(val civInfo: Civilization?): DragListener() {
        private val originalSize = Vector2()
        private var downX: Float = 0f
        private var downY: Float = 0f
        private var dragged = false
        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            originalSize.x = minimap.width
            originalSize.y = minimap.height
            downX = event.stageX
            downY = event.stageY
            return super.touchDown(event, x, y, pointer, button)
        }
        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
            super.touchDragged(event, x, y, pointer)
            if (!isDragging || maximized)
                return
            dragged = true
            val targetSize = Vector2(stage.width - event.stageX, event.stageY)
            // performant way to get the map updated, not changing settings
            minimapSize = minimap.getClosestMinimapSize(targetSize)
            rebuildAndUpdateMap(civInfo)
        }
        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
            super.touchUp(event, x, y, pointer, button)
            if (dragged) {
                worldScreen.game.settings.minimapSize = minimapSize
                GUI.setUpdateWorldOnNextRender() // full update    
            }
        }
    }
}
