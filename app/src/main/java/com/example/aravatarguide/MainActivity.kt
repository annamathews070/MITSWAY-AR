package com.example.aravatarguide

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnHost = findViewById<Button>(R.id.btnHost)
        val btnVisitor = findViewById<Button>(R.id.btnVisitor)

        btnHost.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnVisitor.setOnClickListener {
            val intent = Intent(this, VisitorActivity::class.java)
            startActivity(intent)
        }
    }
}