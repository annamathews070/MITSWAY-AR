package com.example.aravatarguide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.aravatarguide.databinding.ActivityFloorNavigationBinding

class FloorNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFloorNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFloorNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val floors = listOf(
            "Select Floor",
            "1st Floor",
            "2nd Floor",
            "3rd Floor",
            "4th Floor"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            floors
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFloors.adapter = adapter

        binding.spinnerFloors.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val intent = Intent(this@FloorNavigationActivity, VisitorActivity::class.java)
                    startActivity(intent)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
}
