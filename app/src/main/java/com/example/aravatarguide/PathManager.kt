package com.example.aravatarguide

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PathManager(private val context: Context) {

    companion object {
        private const val GRAPH_FILE_NAME = "floor_graph.json"
    }

    fun saveFloorGraph(graph: FloorGraph): Boolean {
        return try {
            val jsonObject = JSONObject()

            // Save all nodes
            val nodesArray = JSONArray()
            for (node in graph.getAllNodes()) {
                val nodeObj = JSONObject().apply {
                    put("id", node.id)
                    put("name", node.name)
                    put("x", node.position[0])
                    put("y", node.position[1])
                    put("z", node.position[2])
                    put("isNamedWaypoint", node.isNamedWaypoint)
                }
                nodesArray.put(nodeObj)
            }
            jsonObject.put("nodes", nodesArray)

            // Save all edges
            val edgesArray = JSONArray()
            val processedEdges = mutableSetOf<String>()

            for (node in graph.getAllNodes()) {
                val edges = graph.getEdges(node.id)
                for (edge in edges) {
                    // Avoid duplicate edges (since graph is bidirectional)
                    val edgeKey = if (edge.from < edge.to) {
                        "${edge.from}-${edge.to}"
                    } else {
                        "${edge.to}-${edge.from}"
                    }

                    if (edgeKey !in processedEdges) {
                        val edgeObj = JSONObject().apply {
                            put("from", edge.from)
                            put("to", edge.to)
                            put("distance", edge.distance)
                        }
                        edgesArray.put(edgeObj)
                        processedEdges.add(edgeKey)
                    }
                }
            }
            jsonObject.put("edges", edgesArray)

            // Write to file
            val file = File(context.filesDir, GRAPH_FILE_NAME)
            FileOutputStream(file).use { fos ->
                fos.write(jsonObject.toString().toByteArray())
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadFloorGraph(): FloorGraph? {
        return try {
            val file = File(context.filesDir, GRAPH_FILE_NAME)
            if (!file.exists()) {
                return null
            }

            val jsonString = FileInputStream(file).use { fis ->
                fis.readBytes().toString(Charsets.UTF_8)
            }

            val jsonObject = JSONObject(jsonString)
            val graph = FloorGraph()

            // Load all nodes
            val nodesArray = jsonObject.getJSONArray("nodes")
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.getJSONObject(i)
                val node = GraphNode(
                    id = nodeObj.getString("id"),
                    name = nodeObj.getString("name"),
                    position = floatArrayOf(
                        nodeObj.getDouble("x").toFloat(),
                        nodeObj.getDouble("y").toFloat(),
                        nodeObj.getDouble("z").toFloat()
                    ),
                    isNamedWaypoint = nodeObj.getBoolean("isNamedWaypoint")
                )
                graph.addNode(node)
            }

            // Load all edges
            val edgesArray = jsonObject.getJSONArray("edges")
            for (i in 0 until edgesArray.length()) {
                val edgeObj = edgesArray.getJSONObject(i)
                graph.addEdge(
                    fromId = edgeObj.getString("from"),
                    toId = edgeObj.getString("to"),
                    distance = edgeObj.getDouble("distance").toFloat()
                )
            }

            graph
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasStoredGraph(): Boolean {
        val file = File(context.filesDir, GRAPH_FILE_NAME)
        return file.exists()
    }

    fun deleteFloorGraph(): Boolean {
        return try {
            val file = File(context.filesDir, GRAPH_FILE_NAME)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getStoredWaypointNames(): List<String> {
        val graph = loadFloorGraph() ?: return emptyList()
        return graph.getNamedWaypoints().map { it.name }
    }
}