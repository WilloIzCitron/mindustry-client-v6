package com.github.blahblahbloopster.navigation

import arc.math.geom.*
import mindustry.Vars.*
import mindustry.client.navigation.*
import mindustry.core.*
import java.util.*
import kotlin.math.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {
    private const val DIAGONAL_COST = 14
    private const val V_H_COST = 10

    //Blocked cells are just null Cell values in grid
    private var grid = CellArray(0, 0)
    private var open = PriorityQueue<Cell>(500)
    private var startX = 0
    private var startY = 0
    private var endX = 0
    private var endY = 0
    private var tileWidth = 0
    private var tileHeight = 0

    @Suppress("NOTHING_TO_INLINE")  // this is fine
    private class CellArray(val width: Int, val height: Int) {
        private val array = Array(width * height) { Cell(x(it), y(it)) }

        private inline fun x(n: Int) = n % width
        private inline fun y(n: Int) = n / width

        private inline fun n(x: Int, y: Int) = (y * width) + x

        operator fun get(x: Int, y: Int) = array[n(x, y)]

        operator fun set(x: Int, y: Int, value: Cell) {
            array[n(x, y)] = value
        }
    }

    private inline fun d8(cons: (x: Int, y: Int) -> Unit) {
        cons(-1, -1)
        cons(-1, 0)
        cons(-1, 1)
        cons(0, -1)
        cons(0, 0)
        cons(0, 1)
        cons(1, -1)
        cons(1, 0)
        cons(1, 1)
    }

    private fun Int.clamp(min: Int, max: Int) = coerceIn(min, max)

    override fun init() {}

    private fun setStartCell(x: Int, y: Int) {
        startX = x
        startY = y
    }

    private fun setEndCell(x: Int, y: Int) {
        endX = x
        endY = y
    }

    private fun checkAndUpdateCost(current: Cell, t: Cell, cost: Int) {
        if (t.closed) return
        val tFinalCost = t.heuristicCost + cost

        val inOpen = open.contains(t)  // O(N)
        if (!inOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost
            t.parent = current
            if (!inOpen) open.add(t) // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        startX = startX.clamp(0, grid.width - 1)
        startY = startY.clamp(0, grid.height - 1)
        open.add(grid[startX, startY])

        endX = endX.clamp(0, grid.width - 1)
        endY = endY.clamp(0, grid.height - 1)

        var current: Cell?
        while (true) {
            current = open.poll() ?: break  // Get a tile to explore
            current.closed = true  // Don't go through it again
            if (current == grid[endX, endY]) {  // Made it to the finish
                return
            }

            // Check surrounding tiles
            d8 { x1, y1 ->
                val x = current.x + x1
                val y = current.y + y1

                if (x < 0 || y < 0 || x >= tileWidth || y >= tileHeight) {
                    return@d8
                }

                // Add to the open list with calculated cost
                checkAndUpdateCost(
                        current,
                        grid[x, y],
                        current.finalCost + grid[x, y].addedCosts
                )
            }
        }
    }

    private fun sq(inp: Float) = inp * inp

    override fun findPath(start: Vec2?, end: Vec2?, obstacles: Array<Circle>?, width: Float, height: Float): Array<Vec2>? {
        start ?: return null
        end ?: return null
        obstacles ?: return null

        tileWidth = ceil(width / tilesize).toInt() + 1
        tileHeight = ceil(height / tilesize).toInt() + 1

        start.clamp(0f, 0f, height, width)
        end.clamp(0f, 0f, height, width)


        if (obstacles.isEmpty()) {
            return arrayOf(end)
        }

        //Reset
        val px = World.toTile(start.x)
        val py = World.toTile(start.y)
        val ex = World.toTile(end.x)
        val ey = World.toTile(end.y)

        if (grid.width != tileWidth || grid.height != tileHeight) {
            grid = CellArray(tileWidth, tileHeight)
        }

        open.clear()

        // Set start position
        setStartCell(px, py)

        // Set End Location
        setEndCell(ex, ey)
        // Reset all cells
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                grid[x, y].finalCost = 0
                grid[x, y].parent = null
                val small = min(abs(x - endX), abs(y - endY))
                grid[x, y].heuristicCost = DIAGONAL_COST * small + V_H_COST * (max(abs(x - endX), abs(y - endY)) - small)
                grid[x, y].closed = false
                grid[x, y].addedCosts = 0
            }
        }

        for (turret in obstacles) {
            val lowerXBound = ((turret.x - turret.radius) / tilesize).toInt().clamp(0, tileWidth)
            val upperXBound = ((turret.x + turret.radius) / tilesize).toInt().clamp(0, tileWidth)
            val lowerYBound = ((turret.y - turret.radius) / tilesize).toInt().clamp(0, tileHeight)
            val upperYBound = ((turret.y + turret.radius) / tilesize).toInt().clamp(0, tileHeight)
            for (x in lowerXBound..upperXBound) {
                val thresh = sq(turret.radius / tilesize) - sq(x.toFloat() - (turret.x / tilesize))

                for (y in lowerYBound..upperYBound) {
                    if (sq((y.toFloat() - (turret.y / tilesize))) <= thresh) {
                        grid[x, y].addedCosts += 1000
                    }
                }
            }
        }

        grid[px, py] = Cell(px, py)

        aStarSearch()

        return if (grid[endX, endY].closed) {
            val points = mutableListOf<Vec2>()
            //Trace back the path
            var current: Cell? = grid[endX, endY]
            while (current?.parent != null) {
                points.add(Vec2(World.unconv(current.parent!!.x.toFloat()), World.unconv(current.parent!!.y.toFloat())))
                current = current.parent
            }
            //            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms");
            points.toTypedArray()
            //            System.out.println();
        } else {
//            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms, no path found");
            null
        }
    }

    class Cell(var x: Int, var y: Int) : Comparable<Cell> {
        var closed = false
        var heuristicCost = 0 //Heuristic cost
        var finalCost = 0 //G+H
        var parent: Cell? = null
        var addedCosts = 0

        override fun toString(): String {
            return "[$x, $y]"
        }

        override fun compareTo(other: Cell): Int {
            return finalCost.compareTo(other.finalCost)
        }
    }
}
