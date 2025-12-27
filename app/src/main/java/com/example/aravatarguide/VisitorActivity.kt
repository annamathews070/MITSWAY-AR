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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.sqrt

class VisitorActivity : AppCompatActivity(), GLSurfaceView.Renderer, TextToSpeech.OnInitListener {

    private var arSession: Session? = null
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvAvatarStatus: TextView
    private lateinit var tvSpeechInput: TextView
    private lateinit var btnMicrophone: Button
    private lateinit var tvAvailableLocations: TextView

    private var installRequested = false
    private var floorGraph: FloorGraph? = null
    private var pathFinder: ShortestPathFinder? = null
    private var currentPath: PathResult? = null
    private var currentWaypointIndex = 0

    private var renderer: SimpleRenderer? = null
    private var backgroundRenderer: BackgroundRenderer? = null
    private val navigationHelper = NavigationHelper()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isTtsReady = false
    private var hasAskedInitialQuestion = false
    private var isNavigating = false
    private var isPositionRecognized = false

    private var arrowModel: ModelLoader? = null
    private var avatarRenderer: AvatarRenderer? = null

    private var pendingDestination: String? = null
    private var userCurrentPosition: FloatArray? = null

    private val pathColor = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f)
    private val namedWaypointColor = floatArrayOf(1.0f, 0.84f, 0.0f, 1.0f)
    private val destinationColor = floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f)

    companion object {
        private const val PERMISSION_CODE = 100
        private const val WAYPOINT_REACHED_DISTANCE = 0.8f
        private const val POSITION_RECOGNITION_DISTANCE = 10.0f // Increased from 5.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content view using resource identifier
        val layoutId = resources.getIdentifier("activity_visitor", "layout", packageName)
        setContentView(layoutId)

        // Find views using resource identifiers
        surfaceView = findViewById(resources.getIdentifier("surfaceView", "id", packageName))
        tvStatus = findViewById(resources.getIdentifier("tvStatus", "id", packageName))
        tvDestination = findViewById(resources.getIdentifier("tvDestination", "id", packageName))
        tvDirection = findViewById(resources.getIdentifier("tvDirection", "id", packageName))
        tvAvatarStatus = findViewById(resources.getIdentifier("tvAvatarStatus", "id", packageName))
        tvSpeechInput = findViewById(resources.getIdentifier("tvSpeechInput", "id", packageName))
        btnMicrophone = findViewById(resources.getIdentifier("btnMicrophone", "id", packageName))
        tvAvailableLocations = findViewById(resources.getIdentifier("tvAvailableLocations", "id", packageName))

        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        btnMicrophone.setOnClickListener { toggleSpeechRecognition() }

        textToSpeech = TextToSpeech(this, this)
        loadFloorGraph()
        checkPermissions()
    }

    private fun loadFloorGraph() {
        val pathManager = PathManager(this)
        floorGraph = pathManager.loadFloorGraph()

        if (floorGraph == null || floorGraph!!.isEmpty()) {
            tvAvailableLocations.text = "⚠️ No floor map found. Use Host mode first."
            tvSpeechInput.text = "No destinations available"
            btnMicrophone.isEnabled = false
            Toast.makeText(this, "No floor map found. Please map the floor first in Host mode.", Toast.LENGTH_LONG).show()
        } else {
            pathFinder = ShortestPathFinder(floorGraph!!)
            val destinations = floorGraph!!.getNamedWaypoints().map { it.name }
            tvAvailableLocations.text = "Available: ${destinations.joinToString(", ")}"
        }
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (!hasCameraPermission()) permissionsNeeded.add(Manifest.permission.CAMERA)
        if (!hasAudioPermission()) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_CODE)
        } else {
            setupSpeechRecognition()
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupSpeechRecognition()
        }
    }

    private fun setupSpeechRecognition() {
        if (!hasAudioPermission()) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                runOnUiThread {
                    tvSpeechInput.text = "🎤 Listening..."
                    btnMicrophone.text = "⏸"
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                runOnUiThread { btnMicrophone.text = "🎤" }
            }
            override fun onError(error: Int) {
                isListening = false
                runOnUiThread {
                    tvSpeechInput.text = "Tap 🎤 to speak"
                    btnMicrophone.text = "🎤"
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    processVoiceCommand(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleSpeechRecognition() {
        if (!hasAudioPermission()) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        if (floorGraph == null || floorGraph!!.isEmpty()) {
            Toast.makeText(this, "No floor map available. Use Host mode first.", Toast.LENGTH_LONG).show()
            return
        }

        if (!isPositionRecognized) {
            Toast.makeText(this, "Please wait while we recognize your position...", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) setupSpeechRecognition()

        if (isListening) {
            speechRecognizer?.stopListening()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Where would you like to go?")
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun processVoiceCommand(command: String) {
        runOnUiThread {
            tvSpeechInput.text = "You said: \"$command\""
        }

        val graph = floorGraph ?: return

        val matchedWaypoint = graph.getNamedWaypoints().find { waypoint ->
            command.contains(waypoint.name, ignoreCase = true) ||
                    waypoint.name.contains(command, ignoreCase = true)
        }

        if (matchedWaypoint != null) {
            startNavigation(matchedWaypoint.name)
        } else {
            val destinations = graph.getNamedWaypoints().map { it.name }
            speak("I couldn't find that location. Available destinations are: ${destinations.joinToString(", ")}")
            runOnUiThread {
                tvSpeechInput.text = "Try saying: ${destinations.joinToString(" or ")}"
            }
        }
    }

    private fun startNavigation(destinationName: String) {
        pendingDestination = destinationName
        runOnUiThread {
            tvSpeechInput.text = "Finding path to $destinationName..."
        }
    }

    private fun processPendingNavigation() {
        val destName = pendingDestination ?: return
        val currentPos = userCurrentPosition ?: return
        val graph = floorGraph ?: return
        val finder = pathFinder ?: return

        try {
            val pathResult = finder.findPathToDestination(currentPos, destName)

            if (pathResult != null) {
                currentPath = pathResult
                currentWaypointIndex = 0
                isNavigating = true

                runOnUiThread {
                    tvDestination.text = "→ $destName"
                    tvDestination.visibility = TextView.VISIBLE
                    tvDirection.visibility = TextView.VISIBLE
                    tvAvatarStatus.text = "🧭 Navigating..."
                    tvAvatarStatus.visibility = TextView.VISIBLE
                }

                speak("Starting navigation to $destName. Total distance is ${pathResult.totalDistance.toInt()} meters. Follow the arrows.")
            } else {
                speak("Sorry, I couldn't find a path to $destName")
                runOnUiThread {
                    Toast.makeText(this, "No path found to $destName", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            speak("Error starting navigation")
            runOnUiThread {
                Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        pendingDestination = null
    }

    private fun recognizeUserPosition(currentPosition: FloatArray) {
        if (isPositionRecognized) return

        val graph = floorGraph ?: return

        // Find the nearest node (any waypoint on the path)
        val nearestNode = graph.findNearestNode(currentPosition)

        if (nearestNode != null) {
            val distance = graph.calculateDistance(currentPosition, nearestNode.position)

            // Recognize within 10 meters of any waypoint
            if (distance < 10.0f) {
                isPositionRecognized = true
                btnMicrophone.isEnabled = true

                // Find the ACTUAL closest named waypoint
                val allNamedWaypoints = graph.getNamedWaypoints()
                var closestNamedWaypoint: GraphNode? = null
                var minDistance = Float.MAX_VALUE

                for (namedWaypoint in allNamedWaypoints) {
                    val dist = graph.calculateDistance(currentPosition, namedWaypoint.position)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestNamedWaypoint = namedWaypoint
                    }
                }

                val locationName = closestNamedWaypoint?.name ?: "the mapped area"

                runOnUiThread {
                    tvStatus.text = "Position recognized near $locationName"
                    tvAvatarStatus.text = "👋 Ready to guide you!"
                    tvAvatarStatus.visibility = TextView.VISIBLE
                }

                if (isTtsReady && !hasAskedInitialQuestion) {
                    hasAskedInitialQuestion = true
                    val destinations = allNamedWaypoints.map { it.name }

                    speak("Hello! You are near $locationName. Where would you like to go? Available destinations are: ${destinations.joinToString(", ")}")
                }
            }
        }
    }

    private fun updateNavigation(currentPosition: FloatArray) {
        val path = currentPath ?: return
        if (!isNavigating || currentWaypointIndex >= path.nodes.size) return

        val graph = floorGraph ?: return
        val targetNode = path.nodes[currentWaypointIndex]
        val distance = graph.calculateDistance(currentPosition, targetNode.position)

        runOnUiThread {
            tvDirection.text = String.format("%.1f", distance) + "m to next waypoint"
        }

        if (distance < WAYPOINT_REACHED_DISTANCE) {
            currentWaypointIndex++

            if (currentWaypointIndex >= path.nodes.size) {
                isNavigating = false
                speak("You have reached your destination")
                runOnUiThread {
                    tvAvatarStatus.text = "✅ Destination Arrived!"
                    tvDirection.text = "You have arrived!"
                }
                currentPath = null
                currentWaypointIndex = 0
            } else {
                val nextNode = path.nodes[currentWaypointIndex]
                if (nextNode.isNamedWaypoint) {
                    speak("Approaching ${nextNode.name}")
                }
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) return

        if (arSession == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                    else -> return
                }
                arSession = Session(this)
                val config = Config(arSession)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                arSession?.configure(config)
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
        }

        try {
            arSession?.resume()
            surfaceView.onResume()
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
            arSession = null
        }
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
        arSession?.pause()
        speechRecognizer?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        backgroundRenderer = BackgroundRenderer()
        backgroundRenderer?.createOnGlThread(this)

        renderer = SimpleRenderer()
        renderer?.createOnGlThread()

        arrowModel = ModelLoader(this)
        arrowModel?.loadModel("arrow.glb")
        arrowModel?.createOnGlThread()

        avatarRenderer = AvatarRenderer()
        avatarRenderer?.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arSession?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = arSession ?: return
        val graph = floorGraph ?: return

        try {
            session.setCameraTextureName(backgroundRenderer?.getTextureId() ?: 0)
            val frame: Frame = session.update()
            val camera = frame.camera

            backgroundRenderer?.draw(frame)

            if (camera.trackingState != TrackingState.TRACKING) {
                runOnUiThread {
                    tvStatus.text = "Initializing AR tracking..."
                }
                return
            }

            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            val cameraPos = floatArrayOf(
                camera.pose.tx(),
                camera.pose.ty(),
                camera.pose.tz()
            )

            userCurrentPosition = cameraPos
            val cameraPose = camera.pose

            if (!isPositionRecognized) {
                recognizeUserPosition(cameraPos)
            }

            if (pendingDestination != null) {
                processPendingNavigation()
            }

            val shouldShowAvatar = if (isNavigating && currentPath != null) {
                currentWaypointIndex >= currentPath!!.nodes.size - 1
            } else {
                true
            }

            val forward = cameraPose.zAxis
            val avatarX = cameraPos[0] - forward[0] * 2.0f
            val avatarY = cameraPos[1] - 1.3f
            val avatarZ = cameraPos[2] - forward[2] * 2.0f

            if (shouldShowAvatar && avatarRenderer != null) {
                avatarRenderer?.draw(viewMatrix, projectionMatrix, avatarX, avatarY, avatarZ)
            }

            if (isNavigating && currentPath != null) {
                val path = currentPath!!

                arrowModel?.let { arrow ->
                    var lastArrowPosition: FloatArray? = null
                    val minArrowDistance = 1.0f

                    for (i in 0 until path.nodes.size - 1) {
                        val currentNode = path.nodes[i]
                        val nextNode = path.nodes[i + 1]

                        val shouldDrawArrow = if (lastArrowPosition == null) {
                            true
                        } else {
                            val distFromLast = graph.calculateDistance(lastArrowPosition, currentNode.position)
                            distFromLast >= minArrowDistance
                        }

                        val isNamedWaypoint = currentNode.isNamedWaypoint

                        if (shouldDrawArrow || isNamedWaypoint) {
                            val dx = nextNode.position[0] - currentNode.position[0]
                            val dz = nextNode.position[2] - currentNode.position[2]
                            val distance = sqrt(dx * dx + dz * dz)

                            if (distance > 0.01f) {
                                val angleY = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()

                                val arrowX = currentNode.position[0]
                                val arrowY = currentNode.position[1] + 0.15f
                                val arrowZ = currentNode.position[2]

                                val arrowModelMatrix = FloatArray(16)
                                Matrix.setIdentityM(arrowModelMatrix, 0)
                                Matrix.translateM(arrowModelMatrix, 0, arrowX, arrowY, arrowZ)
                                Matrix.rotateM(arrowModelMatrix, 0, angleY, 0f, 1f, 0f)
                                Matrix.scaleM(arrowModelMatrix, 0, 0.4f, 0.4f, 0.4f)

                                val arrowMvp = FloatArray(16)
                                val tempMatrix = FloatArray(16)
                                Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, arrowModelMatrix, 0)
                                Matrix.multiplyMM(arrowMvp, 0, projectionMatrix, 0, tempMatrix, 0)

                                val arrowColor = when {
                                    i >= path.nodes.size - 3 -> floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f)
                                    isNamedWaypoint -> floatArrayOf(1.0f, 0.84f, 0.0f, 1.0f)
                                    else -> floatArrayOf(0.2f, 0.6f, 1.0f, 1.0f)
                                }

                                arrow.draw(arrowMvp, arrowColor)
                                lastArrowPosition = currentNode.position
                            }
                        }
                    }

                    renderer?.let { r ->
                        val destNode = path.nodes.last()
                        r.draw(viewMatrix, projectionMatrix, destNode.position[0], destNode.position[1], destNode.position[2], destinationColor)
                    }
                }

                updateNavigation(cameraPos)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}