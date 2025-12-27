package com.example.aravatarguide

import com.google.ar.core.Pose
import java.util.UUID
import kotlin.math.sqrt

class PathRecorder {

    private val floorGraph = FloorGraph()
    private var isRecording = false
    private var lastRecordedPosition: FloatArray? = null
    private var lastNodeId: String? = null
    private var startPointName: String = ""

    companion object {
        private const val MIN_DISTANCE_BETWEEN_POINTS = 0.3f // 30cm
    }

    fun startRecording(startName: String) {
        isRecording = true
        startPointName = startName
        lastRecordedPosition = null
        lastNodeId = null
        floorGraph.clear()

        // We'll add the start point when we get the first position
    }

    fun updatePosition(pose: Pose): Boolean {
        if (!isRecording) return false

        val currentPos = floatArrayOf(pose.tx(), pose.ty(), pose.tz())

        // Check if this is the first point
        if (lastRecordedPosition == null) {
            // Create starting point as a NAMED waypoint
            val nodeId = UUID.randomUUID().toString()
            val node = GraphNode(
                id = nodeId,
                name = startPointName,
                position = currentPos.clone(),
                isNamedWaypoint = true
            )
            floorGraph.addNode(node)
            lastRecordedPosition = currentPos.clone()
            lastNodeId = nodeId
            return true
        }

        // Check if we should create a new intermediate waypoint
        if (shouldCreateWaypoint(currentPos)) {
            val nodeId = UUID.randomUUID().toString()

            // Create intermediate (unnamed) waypoint
            val node = GraphNode(
                id = nodeId,
                name = "", // Empty name for intermediate waypoints
                position = currentPos.clone(),
                isNamedWaypoint = false
            )

            floorGraph.addNode(node)

            // Connect to previous node
            lastNodeId?.let { prevId ->
                val distance = calculateDistance(lastRecordedPosition!!, currentPos)
                floorGraph.addEdge(prevId, nodeId, distance)
            }

            lastRecordedPosition = currentPos.clone()
            lastNodeId = nodeId
            return true
        }

        return false
    }

    fun markNamedWaypoint(pose: Pose, name: String): Boolean {
        if (name.isBlank()) return false

        // Check if name already exists
        if (floorGraph.getNodeByName(name) != null) {
            return false // Duplicate name
        }

        val position = floatArrayOf(pose.tx(), pose.ty(), pose.tz())
        val nodeId = UUID.randomUUID().toString()

        val node = GraphNode(
            id = nodeId,
            name = name,
            position = position.clone(),
            isNamedWaypoint = true
        )

        floorGraph.addNode(node)

        // Connect to previous node if exists
        lastNodeId?.let { prevId ->
            val distance = calculateDistance(
                lastRecordedPosition ?: position,
                position
            )
            floorGraph.addEdge(prevId, nodeId, distance)
        }

        lastRecordedPosition = position.clone()
        lastNodeId = nodeId

        return true
    }

    private fun shouldCreateWaypoint(currentPos: FloatArray): Boolean {
        val lastPos = lastRecordedPosition ?: return true
        val distance = calculateDistance(lastPos, currentPos)
        return distance >= MIN_DISTANCE_BETWEEN_POINTS
    }

    private fun calculateDistance(pos1: FloatArray, pos2: FloatArray): Float {
        val dx = pos1[0] - pos2[0]
        val dy = pos1[1] - pos2[1]
        val dz = pos1[2] - pos2[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun stopRecording(): FloorGraph {
        isRecording = false
        return floorGraph
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getWaypointCount(): Int = floorGraph.getNodeCount()

    fun getNamedWaypointCount(): Int = floorGraph.getNamedWaypointCount()

    fun getRecordedPoints(): List<GraphNode> = floorGraph.getAllNodes()
}