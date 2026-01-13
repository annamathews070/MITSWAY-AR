package com.example.aravatarguide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aravatarguide.databinding.ActivityVisitorBinding
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.sqrt

class VisitorActivity : AppCompatActivity(), GLSurfaceView.Renderer, TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityVisitorBinding

    private var arSession: Session? = null
    private var installRequested = false

    private var backgroundRenderer: BackgroundRenderer? = null
    private var renderer: SimpleRenderer? = null
    private var avatarRenderer: AvatarRenderer? = null
    private var arrowModel: ModelLoader? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var floorGraph: FloorGraph? = null
    private var pathFinder: ShortestPathFinder? = null
    private var currentPath: PathResult? = null
    private var currentWaypointIndex = 0

    private var isTtsReady = false
    private var isListening = false
    private var hasAskedInitialQuestion = false
    private var isPositionRecognized = false
    private var isNavigating = false

    private var userCurrentPosition: List<Float>? = null
    private var pendingDestination: String? = null

    private val destinationColor = floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f)
    private val arrowColor = floatArrayOf(0.0f, 0.5f, 1.0f, 1.0f) // Blue arrows

    private lateinit var database: FirebaseDatabase
    private lateinit var firebasePathManager: FirebasePathManager

    companion object {
        private const val PERMISSION_CODE = 100
        private const val WAYPOINT_REACHED_DISTANCE = 0.8f
        private const val ARROW_SPACING = 0.8f // Distance between arrows (meters)
        private const val ARROW_HEIGHT_OFFSET = 0.1f // Height above ground
        private const val TAG = "VisitorActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance()
        firebasePathManager = FirebasePathManager()

        binding.surfaceView.preserveEGLContextOnPause = true
        binding.surfaceView.setEGLContextClientVersion(2)
        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        binding.btnMap.setOnClickListener { startActivity(Intent(this, BuildingActivity::class.java)) }
        binding.btnMicrophone.setOnClickListener { toggleSpeechRecognition() }
        binding.btnMicrophone.isEnabled = false

        textToSpeech = TextToSpeech(this, this)
        loadFloorGraphFromFirebase()
        checkPermissions()

        intent.getStringExtra("destination")?.let { startNavigation(it) }
    }

    private fun loadFloorGraphFromFirebase() {
        runOnUiThread {
            binding.tvStatus.text = "Downloading map..."
            binding.tvAvailableLocations.text = "Loading map from cloud..."
        }

        firebasePathManager.loadFloorGraph { graph ->
            Log.d(TAG, "Received graph: ${graph?.getNodeCount() ?: 0} nodes")

            if (graph != null && !graph.isEmpty()) {
                floorGraph = graph
                pathFinder = ShortestPathFinder(floorGraph!!)
                val destinations = floorGraph!!.getNamedWaypoints().map { it.name }

                runOnUiThread {
                    binding.initialStateContainer.visibility = View.VISIBLE
                    binding.tvAvailableLocations.text = "Available Locations:\n${destinations.joinToString("\n")}"
                    binding.tvStatus.text = "Map loaded! Looking for your position..."
                    Toast.makeText(this, "✅ Map loaded: ${destinations.size} locations", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Available locations: $destinations")
                }
                listenForEmergency()
            } else {
                runOnUiThread {
                    binding.initialStateContainer.visibility = View.VISIBLE
                    binding.tvAvailableLocations.text = "⚠️ No map found in cloud."
                    binding.tvSpeechInput.text = "Please create a map in Host Mode first."
                    binding.btnMicrophone.isEnabled = false
                    binding.tvStatus.text = "No map available"
                    Toast.makeText(this, "❌ No map available. Use Host Mode to create one.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_CODE)
        } else {
            setupSpeechRecognition()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupSpeechRecognition()
        }
    }

    private fun setupSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    runOnUiThread { binding.tvSpeechInput.text = "🎤 Listening..." }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    runOnUiThread { binding.tvSpeechInput.text = "Tap 🎤 to speak" }
                }
                override fun onError(error: Int) {
                    isListening = false
                    Log.e(TAG, "Speech recognition error: $error")
                    runOnUiThread { binding.tvSpeechInput.text = "Tap 🎤 to speak" }
                }
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                        processVoiceCommand(it)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun toggleSpeechRecognition() {
        if (floorGraph == null || !isPositionRecognized) {
            Toast.makeText(this, "Please wait for position to be recognized.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListening) {
            speechRecognizer?.stopListening()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun processVoiceCommand(command: String) {
        runOnUiThread { binding.tvSpeechInput.text = "You said: \"$command\"" }
        Log.d(TAG, "Processing voice command: $command")

        val matchedWaypoint = floorGraph?.getNamedWaypoints()?.find {
            command.contains(it.name, ignoreCase = true)
        }

        if (matchedWaypoint != null) {
            Log.d(TAG, "Matched waypoint: ${matchedWaypoint.name}")
            startNavigation(matchedWaypoint.name)
        } else {
            speak("I couldn't find that location. Available locations are: ${floorGraph?.getNamedWaypoints()?.joinToString { it.name }}")
        }
    }

    private fun startNavigation(destinationName: String) {
        Log.d(TAG, "Starting navigation to: $destinationName")
        pendingDestination = destinationName
        isNavigating = true
        runOnUiThread {
            binding.initialStateContainer.visibility = View.GONE
            binding.tvStatus.text = "Finding path to $destinationName..."
        }
    }

    private fun processPendingNavigation() {
        val destName = pendingDestination ?: return
        val currentPos = userCurrentPosition ?: return
        val graph = floorGraph ?: return
        val finder = pathFinder ?: return

        try {
            Log.d(TAG, "Finding path from $currentPos to $destName")
            val pathResult = finder.findPathToDestination(currentPos, destName)

            if (pathResult != null) {
                currentPath = pathResult
                currentWaypointIndex = 0
                Log.d(TAG, "Path found with ${pathResult.nodes.size} waypoints, distance: ${pathResult.totalDistance}m")

                runOnUiThread {
                    binding.tvDestination.text = "→ $destName"
                    binding.tvDestination.visibility = View.VISIBLE
                    binding.tvDirection.visibility = View.VISIBLE
                    binding.tvStatus.text = "Navigate to $destName"
                }
                speak("Starting navigation to $destName. Follow the arrows.")
            } else {
                Log.w(TAG, "No path found to $destName")
                speak("Sorry, I couldn't find a path to $destName")
                isNavigating = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting navigation", e)
            speak("Error starting navigation")
            isNavigating = false
        }
        pendingDestination = null
    }

    private fun recognizeUserPosition(currentPosition: List<Float>) {
        if (isPositionRecognized || floorGraph == null) return

        val closestNamedWaypoint = floorGraph!!.findNearestNamedWaypoint(currentPosition)

        if (closestNamedWaypoint != null) {
            isPositionRecognized = true
            userCurrentPosition = currentPosition
            Log.d(TAG, "Position recognized near: ${closestNamedWaypoint.name}")

            runOnUiThread {
                binding.tvStatus.text = "Position recognized near ${closestNamedWaypoint.name}"
                binding.btnMicrophone.isEnabled = true
                Toast.makeText(this, "✅ Position recognized!", Toast.LENGTH_SHORT).show()
            }

            if (isTtsReady && !hasAskedInitialQuestion && pendingDestination == null) {
                hasAskedInitialQuestion = true
                speak("Hello! You are near ${closestNamedWaypoint.name}. Where would you like to go?")
            }
        }
    }

    private fun updateNavigation(currentPosition: List<Float>) {
        val path = currentPath ?: return
        if (!isNavigating || currentWaypointIndex >= path.nodes.size) return

        val targetNode = path.nodes[currentWaypointIndex]
        val distance = floorGraph!!.calculateDistanceFromFloat(currentPosition, targetNode.position)

        runOnUiThread {
            binding.tvDirection.text = String.format("%.1f m to next point", distance)
        }

        if (distance < WAYPOINT_REACHED_DISTANCE) {
            currentWaypointIndex++
            if (currentWaypointIndex >= path.nodes.size) {
                isNavigating = false
                currentPath = null
                speak("You have reached your destination")
                runOnUiThread {
                    binding.tvDirection.text = "✅ You have arrived!"
                    binding.tvStatus.text = "Destination reached"
                }
            } else {
                val nextNode = path.nodes[currentWaypointIndex]
                if (nextNode.isNamedWaypoint) {
                    speak("Approaching ${nextNode.name}")
                }
            }
        }
    }

    // NEW: Generate continuous arrow positions along the path
    private fun generateContinuousArrows(path: List<GraphNode>): List<ArrowPosition> {
        val arrows = mutableListOf<ArrowPosition>()

        for (i in 0 until path.size - 1) {
            val start = path[i]
            val end = path[i + 1]

            val startX = start.position[0].toFloat()
            val startY = start.position[1].toFloat()
            val startZ = start.position[2].toFloat()

            val endX = end.position[0].toFloat()
            val endY = end.position[1].toFloat()
            val endZ = end.position[2].toFloat()

            val dx = endX - startX
            val dy = endY - startY
            val dz = endZ - startZ

            val segmentLength = sqrt(dx * dx + dy * dy + dz * dz)

            if (segmentLength < 0.1f) continue

            // Calculate angle for arrow rotation
            val angleY = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()

            // Calculate number of arrows based on spacing
            var distance = 0f
            while (distance < segmentLength) {
                val t = distance / segmentLength

                val arrowX = startX + dx * t
                val arrowY = startY + dy * t + ARROW_HEIGHT_OFFSET
                val arrowZ = startZ + dz * t

                arrows.add(ArrowPosition(arrowX, arrowY, arrowZ, angleY))

                distance += ARROW_SPACING
            }
        }

        Log.d(TAG, "Generated ${arrows.size} arrows for path")
        return arrows
    }

    private fun listenForEmergency() {
        database.getReference("emergency").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    triggerEmergencyEvacuation()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Emergency listener cancelled: ${error.message}")
            }
        })
    }

    private fun triggerEmergencyEvacuation() {
        if (floorGraph == null || userCurrentPosition == null) return

        speak("EMERGENCY! EVACUATE IMMEDIATELY!")
        Log.d(TAG, "Emergency triggered, finding nearest exit")

        val closestExit = floorGraph!!.getEmergencyExits().minByOrNull {
            floorGraph!!.calculateDistanceFromFloat(userCurrentPosition!!, it.position)
        }

        if (closestExit != null) {
            startNavigation(closestExit.name)
        } else {
            speak("No emergency exits have been defined!")
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
        Log.d(TAG, "TTS: $text")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) return
        if (arSession == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true
                    return
                }
                arSession = Session(this)
                arSession?.configure(Config(arSession).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AR session", e)
                return
            }
        }
        try {
            arSession?.resume()
            binding.surfaceView.onResume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
            arSession = null
        }
    }

    override fun onPause() {
        super.onPause()
        binding.surfaceView.onPause()
        arSession?.pause()
        speechRecognizer?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        backgroundRenderer = BackgroundRenderer().apply { createOnGlThread(this@VisitorActivity) }
        renderer = SimpleRenderer().apply { createOnGlThread() }
        avatarRenderer = AvatarRenderer().apply { createOnGlThread() }
        arrowModel = ModelLoader(this).apply {
            loadModel("arrow.glb")
            createOnGlThread()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arSession?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arSession ?: return

        try {
            session.setCameraTextureName(backgroundRenderer?.getTextureId() ?: 0)
            val frame = session.update()
            val camera = frame.camera

            backgroundRenderer?.draw(frame)

            if (camera.trackingState != TrackingState.TRACKING) return

            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            val cameraPos = listOf(camera.pose.tx(), camera.pose.ty(), camera.pose.tz())

            if (!isPositionRecognized) {
                recognizeUserPosition(cameraPos)
            }

            if (pendingDestination != null) {
                processPendingNavigation()
            }

            if (isNavigating && currentPath != null) {
                updateNavigation(cameraPos)

                val path = currentPath!!

                // Generate and draw continuous arrows from current waypoint to destination
                val remainingPath = path.nodes.drop(currentWaypointIndex)
                val arrows = generateContinuousArrows(remainingPath)

                Log.d(TAG, "Drawing ${arrows.size} arrows, current waypoint: $currentWaypointIndex/${path.nodes.size}")

                arrowModel?.let { arrow ->
                    for (arrowPos in arrows) {
                        val modelMatrix = FloatArray(16)
                        Matrix.setIdentityM(modelMatrix, 0)
                        Matrix.translateM(modelMatrix, 0, arrowPos.x, arrowPos.y, arrowPos.z)
                        Matrix.rotateM(modelMatrix, 0, arrowPos.rotation, 0f, 1f, 0f)
                        Matrix.scaleM(modelMatrix, 0, 0.35f, 0.35f, 0.35f)

                        val mvpMatrix = FloatArray(16)
                        val tempMatrix = FloatArray(16)
                        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
                        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

                        arrow.draw(mvpMatrix, arrowColor)
                    }
                }

                // Draw destination marker
                renderer?.let { r ->
                    val destNode = path.nodes.last()
                    val pos = destNode.toFloatArray()
                    r.draw(viewMatrix, projectionMatrix, pos[0], pos[1], pos[2], destinationColor)
                }
            } else {
                // Show avatar when not navigating
                val avatarX = cameraPos[0] - (camera.pose.zAxis[0] * 2.0f)
                val avatarY = cameraPos[1] - 1.5f
                val avatarZ = cameraPos[2] - (camera.pose.zAxis[2] * 2.0f)
                avatarRenderer?.draw(viewMatrix, projectionMatrix, avatarX, avatarY, avatarZ)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrawFrame", e)
        }
    }

    // Helper data class for arrow positions
    data class ArrowPosition(
        val x: Float,
        val y: Float,
        val z: Float,
        val rotation: Float
    )
}