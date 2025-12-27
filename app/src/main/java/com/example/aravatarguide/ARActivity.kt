package com.example.aravatarguide

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private var arSession: Session? = null
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var tvInstruction: TextView
    private lateinit var tvRecordingStatus: TextView
    private lateinit var tvWaypointCount: TextView
    private lateinit var tvPathInfo: TextView
    private lateinit var etStartPoint: EditText
    private lateinit var btnStartRecording: Button
    private lateinit var btnMarkWaypoint: Button
    private lateinit var btnStopRecording: Button
    private lateinit var layoutStartPoint: LinearLayout

    private var installRequested = false
    private var renderer: SimpleRenderer? = null
    private var backgroundRenderer: BackgroundRenderer? = null
    private val pathRecorder = PathRecorder()
    private var pendingWaypointName: String? = null

    private val waypointColor = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f) // Green
    private val namedWaypointColor = floatArrayOf(1.0f, 0.84f, 0.0f, 1.0f) // Gold

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aractivity)

        // Initialize views
        surfaceView = findViewById(R.id.surfaceView)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        tvWaypointCount = findViewById(R.id.tvWaypointCount)
        tvPathInfo = findViewById(R.id.tvPathInfo)
        etStartPoint = findViewById(R.id.etStartPoint)
        btnStartRecording = findViewById(R.id.btnStartRecording)
        btnMarkWaypoint = findViewById(R.id.btnMarkWaypoint)
        btnStopRecording = findViewById(R.id.btnStopRecording)
        layoutStartPoint = findViewById(R.id.layoutStartPoint)

        // Setup OpenGL
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // Setup buttons
        btnStartRecording.setOnClickListener { startRecording() }
        btnMarkWaypoint.setOnClickListener { showNameWaypointDialog() }
        btnStopRecording.setOnClickListener { stopRecording() }

        // Initially hide these buttons
        btnMarkWaypoint.visibility = View.GONE
        btnStopRecording.visibility = View.GONE

        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }

    private fun startRecording() {
        val startName = etStartPoint.text.toString().trim()

        if (startName.isEmpty()) {
            Toast.makeText(this, "Please enter starting point name", Toast.LENGTH_SHORT).show()
            return
        }

        pathRecorder.startRecording(startName)

        // Update UI
        runOnUiThread {
            layoutStartPoint.visibility = View.GONE
            btnMarkWaypoint.visibility = View.VISIBLE
            btnStopRecording.visibility = View.VISIBLE
            tvRecordingStatus.visibility = View.VISIBLE
            tvWaypointCount.visibility = View.VISIBLE
            tvPathInfo.visibility = View.VISIBLE
            tvInstruction.text = "Walk around and mark important waypoints"
            tvPathInfo.text = "Recording: $startName (Starting Point)"
            hideKeyboard()

            Toast.makeText(this, "🎬 Recording started from $startName", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNameWaypointDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_name_waypoint, null)
        val editTextName = dialogView.findViewById<EditText>(R.id.etWaypointName)

        AlertDialog.Builder(this)
            .setTitle("Name This Waypoint")
            .setMessage("Enter a name for this major location")
            .setView(dialogView)
            .setPositiveButton("Mark") { _, _ ->
                val name = editTextName.text.toString().trim()
                markCurrentPositionAsWaypoint(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markCurrentPositionAsWaypoint(name: String) {
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a waypoint name", Toast.LENGTH_SHORT).show()
            return
        }

        if (!pathRecorder.isCurrentlyRecording()) {
            Toast.makeText(this, "Recording is not active", Toast.LENGTH_SHORT).show()
            return
        }

        // Store the name to be marked on the next AR frame
        pendingWaypointName = name
        Toast.makeText(this, "Marking waypoint '$name'...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        val graph = pathRecorder.stopRecording()

        if (graph.getNamedWaypointCount() < 2) {
            Toast.makeText(
                this,
                "⚠️ Please mark at least 2 named waypoints before saving",
                Toast.LENGTH_LONG
            ).show()
            // Allow to continue recording
            pathRecorder.startRecording(etStartPoint.text.toString().trim())
            return
        }

        // Save the floor graph
        val pathManager = PathManager(this)
        if (pathManager.saveFloorGraph(graph)) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "✅ Floor map saved!\n" +
                            "Total waypoints: ${graph.getNodeCount()}\n" +
                            "Named waypoints: ${graph.getNamedWaypointCount()}",
                    Toast.LENGTH_LONG
                ).show()

                // Reset UI
                tvRecordingStatus.visibility = View.GONE
                tvWaypointCount.visibility = View.GONE
                tvPathInfo.visibility = View.GONE
                btnMarkWaypoint.visibility = View.GONE
                btnStopRecording.visibility = View.GONE
                layoutStartPoint.visibility = View.VISIBLE
                etStartPoint.text.clear()
                tvInstruction.text = "Floor map saved! Create another or go back."
            }
        } else {
            Toast.makeText(this, "❌ Failed to save floor map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWaypointCount() {
        val totalCount = pathRecorder.getWaypointCount()
        val namedCount = pathRecorder.getNamedWaypointCount()
        tvWaypointCount.text = "Waypoints: $totalCount | Named: $namedCount"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etStartPoint.windowToken, 0)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        backgroundRenderer = BackgroundRenderer()
        backgroundRenderer?.createOnGlThread(this)

        renderer = SimpleRenderer()
        renderer?.createOnGlThread()
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
            val frame: Frame = session.update()
            val camera = frame.camera

            // Draw camera background
            backgroundRenderer?.draw(frame)

            if (camera.trackingState != TrackingState.TRACKING) {
                return
            }

            // Record waypoint if recording
            if (pathRecorder.isCurrentlyRecording()) {
                // Check if we have a pending named waypoint to mark
                val pendingName = pendingWaypointName
                if (pendingName != null) {
                    val success = pathRecorder.markNamedWaypoint(camera.pose, pendingName)
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this@ARActivity, "✅ '$pendingName' marked!", Toast.LENGTH_SHORT).show()
                            updateWaypointCount()
                        } else {
                            Toast.makeText(this@ARActivity, "❌ Name already exists", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingWaypointName = null // Clear the pending name
                } else {
                    // Regular automatic waypoint recording
                    val recorded = pathRecorder.updatePosition(camera.pose)
                    if (recorded) {
                        runOnUiThread {
                            updateWaypointCount()
                        }
                    }
                }
            }

            // Draw recorded waypoints
            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            renderer?.let { r ->
                pathRecorder.getRecordedPoints().forEach { node ->
                    val color = if (node.isNamedWaypoint) namedWaypointColor else waypointColor
                    r.draw(viewMatrix, projectionMatrix, node.position[0], node.position[1], node.position[2], color)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}