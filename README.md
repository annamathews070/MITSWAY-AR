# 🧭 MITSWAY-AR  
**AI-Powered Augmented Reality Campus Navigation System**

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![ARCore](https://img.shields.io/badge/ARCore-4285F4?style=for-the-badge&logo=google&logoColor=white)
![AI](https://img.shields.io/badge/AI-Navigation-FF6F00?style=for-the-badge)

---

## 📖 Overview

**MITSWAY-AR** is an **AI-powered augmented reality navigation system** designed to simplify navigation inside large campuses such as universities, hospitals, and industrial facilities. Instead of relying on confusing 2D maps, users are guided using **3D AR arrows, waypoints, and voice assistance** directly in their real-world view.

The system uses **visual-inertial tracking, intelligent routing, and ARCore** to provide an immersive and intuitive navigation experience.

---

## ✨ Key Features

- 🧭 **AR Navigation Overlay**  
  3D arrows, paths, and markers appear in the real environment.

- 🧠 **AI-Based Path Planning**  
  Shortest and most efficient routes are computed automatically.

- 🎙️ **Voice-Assisted Guidance**  
  Spoken directions help users navigate hands-free.

- 📍 **Visual-Inertial Tracking**  
  Accurate positioning using ARCore motion tracking.

- 🚨 **Emergency Routing (Planned)**  
  Designed to support evacuation and safety guidance.

- 🏫 **Campus-Scale Navigation**  
  Optimized for large institutions like universities and hospitals.

---

## 🎬 How It Works

### 🗺️ Navigation Flow

1. User selects or speaks a destination  
2. AI engine calculates the optimal route  
3. ARCore maps the real environment  
4. 3D arrows and waypoints appear in AR  
5. Voice guidance directs the user step-by-step  
6. System confirms arrival at destination  

---

## 🛠️ Technical Stack

### Core Technologies

- **Platform:** Android  
- **Language:** Kotlin  
- **AR Framework:** Google ARCore  
- **Tracking:** Visual-Inertial Odometry  
- **Pathfinding:** Graph-based shortest path  
- **AI Layer:** Context-aware route selection  
- **Voice:** Android Text-to-Speech  

---

## 🧩 Major Components

| Component | Purpose |
|--------|---------|
| `VisitorActivity` | AR navigation and user interface |
| `NavigationHelper` | Route following & distance logic |
| `PathFinder` | Computes shortest navigation path |
| `PathManager` | Stores and manages route data |
| `ModelLoader` | Loads 3D arrows & markers |
| `SimpleRenderer` | Displays AR waypoints |
| `BackgroundRenderer` | AR camera feed |

---

## 📋 Requirements

- Android 7.0 (API 24+)  
- ARCore supported device  
- Camera & motion sensors  
- Internet (optional for updates)  

---

## 🚀 Getting Started

### 1️⃣ Clone the Repository
```bash
git clone https://github.com/annamathews070/MITSWAY-AR.git
cd MITSWAY-AR
