package com.example.aravatarguide

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aravatarguide.databinding.ActivityPlacesBinding

class PlacesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlacesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val building = intent.getStringExtra("building")
        binding.tvBuildingName.text = building

        if (building == "M George") {
            binding.btnPrincipalOffice.setOnClickListener {
                showDescription("Principal Office", "The head of the institution's office.")
            }
            binding.btnAPJHall.setOnClickListener {
                showDescription("APJ Hall", "A large hall for events and seminars.")
            }
        } else {
            // Handle other buildings here
        }
    }

    private fun showDescription(place: String, description: String) {
        Toast.makeText(this, "$place: $description", Toast.LENGTH_LONG).show()
    }
}