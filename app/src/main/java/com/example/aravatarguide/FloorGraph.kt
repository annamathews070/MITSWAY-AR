package com.example.aravatarguide

import kotlin.math.sqrt

data class GraphNode(
    val id: String,
    val name: String,
    val position: FloatArray,
    val isNamedWaypoint: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GraphNode
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class GraphEdge(
    val from: String,
    val to: String,
    val distance: Float
)

class FloorGraph {
    private val nodes = mutableMapOf<String, GraphNode>()
    private val adjacencyList = mutableMapOf<String, MutableList<GraphEdge>>()

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
        if (!adjacencyList.containsKey(node.id)) {
            adjacencyList[node.id] = mutableListOf()
        }
    }

    fun addEdge(fromId: String, toId: String, distance: Float) {
        adjacencyList[fromId]?.add(GraphEdge(fromId, toId, distance))
        adjacencyList[toId]?.add(GraphEdge(toId, fromId, distance))
    }

    fun getAllNodes(): List<GraphNode> {
        return nodes.values.toList()
    }

    fun getNamedWaypoints(): List<GraphNode> {
        return nodes.values.filter { it.isNamedWaypoint }
    }

    fun getNode(id: String): GraphNode? {
        return nodes[id]
    }

    fun getEdges(nodeId: String): List<GraphEdge> {
        return adjacencyList[nodeId] ?: emptyList()
    }

    fun calculateDistance(pos1: FloatArray, pos2: FloatArray): Float {
        val dx = pos1[0] - pos2[0]
        val dy = pos1[1] - pos2[1]
        val dz = pos1[2] - pos2[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun findNearestNode(position: FloatArray): GraphNode? {
        var nearestNode: GraphNode? = null
        var minDistance = Float.MAX_VALUE

        for (node in nodes.values) {
            val distance = calculateDistance(position, node.position)
            if (distance < minDistance) {
                minDistance = distance
                nearestNode = node
            }
        }

        return nearestNode
    }

    fun findNearestNamedWaypoint(position: FloatArray): GraphNode? {
        var nearestNode: GraphNode? = null
        var minDistance = Float.MAX_VALUE

        for (node in nodes.values.filter { it.isNamedWaypoint }) {
            val distance = calculateDistance(position, node.position)
            if (distance < minDistance) {
                minDistance = distance
                nearestNode = node
            }
        }

        return nearestNode
    }

    fun getNodeByName(name: String): GraphNode? {
        return nodes.values.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    fun isEmpty(): Boolean {
        return nodes.isEmpty()
    }

    fun getNodeCount(): Int {
        return nodes.size
    }

    fun getNamedWaypointCount(): Int {
        return nodes.values.count { it.isNamedWaypoint }
    }

    fun clear() {
        nodes.clear()
        adjacencyList.clear()
    }
}