package com.example.aravatarguide

import java.util.PriorityQueue

/**
 * Path result containing the sequence of nodes and total distance
 */
data class PathResult(
    val nodes: List<GraphNode>,
    val totalDistance: Float
)

/**
 * Implements Dijkstra's shortest path algorithm for floor navigation
 */
class ShortestPathFinder(private val graph: FloorGraph) {

    private data class NodeDistance(
        val nodeId: String,
        val distance: Float
    ) : Comparable<NodeDistance> {
        override fun compareTo(other: NodeDistance): Int {
            return distance.compareTo(other.distance)
        }
    }

    /**
     * Find shortest path between two nodes using Dijkstra's algorithm
     */
    fun findShortestPath(startNodeId: String, endNodeId: String): PathResult? {
        // Validate nodes exist
        if (graph.getNode(startNodeId) == null || graph.getNode(endNodeId) == null) {
            return null
        }

        // If start and end are the same
        if (startNodeId == endNodeId) {
            val node = graph.getNode(startNodeId)
            return node?.let { PathResult(listOf(it), 0f) }
        }

        // Initialize data structures
        val distances = mutableMapOf<String, Float>()
        val previous = mutableMapOf<String, String?>()
        val visited = mutableSetOf<String>()
        val priorityQueue = PriorityQueue<NodeDistance>()

        // Initialize all distances to infinity except start
        for (node in graph.getAllNodes()) {
            distances[node.id] = Float.MAX_VALUE
            previous[node.id] = null
        }
        distances[startNodeId] = 0f

        priorityQueue.add(NodeDistance(startNodeId, 0f))

        // Dijkstra's algorithm
        while (priorityQueue.isNotEmpty()) {
            val current = priorityQueue.poll()
            val currentId = current.nodeId

            // Skip if already visited
            if (currentId in visited) continue
            visited.add(currentId)

            // Found destination
            if (currentId == endNodeId) {
                break
            }

            // Check all neighbors
            val edges = graph.getEdges(currentId)
            for (edge in edges) {
                val neighborId = edge.to

                if (neighborId in visited) continue

                val newDistance = distances[currentId]!! + edge.distance

                if (newDistance < distances[neighborId]!!) {
                    distances[neighborId] = newDistance
                    previous[neighborId] = currentId
                    priorityQueue.add(NodeDistance(neighborId, newDistance))
                }
            }
        }

        // Reconstruct path
        if (previous[endNodeId] == null && startNodeId != endNodeId) {
            return null // No path found
        }

        val path = mutableListOf<String>()
        var current: String? = endNodeId

        while (current != null) {
            path.add(0, current)
            current = previous[current]
        }

        // Convert node IDs to GraphNode objects
        val nodePath = path.mapNotNull { graph.getNode(it) }
        val totalDistance = distances[endNodeId] ?: 0f

        return PathResult(nodePath, totalDistance)
    }

    /**
     * Find shortest path from current position to a named destination
     */
    fun findPathToDestination(
        currentPosition: FloatArray,
        destinationName: String
    ): PathResult? {
        // Find nearest node to current position
        val startNode = graph.findNearestNode(currentPosition)
            ?: return null

        // Find destination node by name
        val endNode = graph.getNodeByName(destinationName)
            ?: return null

        return findShortestPath(startNode.id, endNode.id)
    }

    /**
     * Find shortest path between two named waypoints
     */
    fun findPathBetweenWaypoints(
        startName: String,
        endName: String
    ): PathResult? {
        val startNode = graph.getNodeByName(startName)
            ?: return null

        val endNode = graph.getNodeByName(endName)
            ?: return null

        return findShortestPath(startNode.id, endNode.id)
    }

    /**
     * Get all named waypoints sorted by distance from current position
     */
    fun getNearbyWaypoints(
        currentPosition: FloatArray,
        maxResults: Int = 5
    ): List<Pair<GraphNode, Float>> {
        val namedWaypoints = graph.getNamedWaypoints()

        return namedWaypoints.map { waypoint ->
            val distance = graph.calculateDistance(currentPosition, waypoint.position)
            Pair(waypoint, distance)
        }.sortedBy { it.second }
            .take(maxResults)
    }
}