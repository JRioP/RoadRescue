# RoadRescue: "Your Journey, Our Priority" 🚗🛠️

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android)
![Language](https://img.shields.io/badge/Language-Java-ED8B00?style=flat&logo=java)
![Backend](https://img.shields.io/badge/Backend-Firebase-FFCA28?style=flat&logo=firebase)
![Status](https://img.shields.io/badge/Status-Capstone%20Project-blue)

> **Mobile Application for Roadside Emergency Support**

---

## 📋 Table of Contents
- [Overview](#-overview)
- [Key Features](#-key-features)
- [How It Works](#-how-it-works)
- [Tech Stack](#-tech-stack)
- [Screenshots](#-screenshots)
- [Getting Started](#-getting-started)
- [Authors](#-authors)

---

## 📖 Overview
**RoadRescue** is a native Android roadside assistance system designed to connect stranded motorists with nearby service providers in the Philippines efficiently.

Traditional roadside assistance often suffers from slow response times, weak coordination, and lack of transparency. **RoadRescue** solves this by integrating **real-time GPS tracking**, **automated service provider matching**, and **secure cashless payments** into a unified platform.

Whether you are dealing with a flat tire, engine breakdown, or need towing, RoadRescue ensures help is just one tap away.

---

## 🌟 Key Features

### 🚙 For Motorists (Users)
| Feature | Description |
| :--- | :--- |
| **📍 Real-Time GPS Tracking** | Live monitoring of service provider location with accurate ETA updates via Google Maps. |
| **🛠️ One-Tap Assistance** | Instant requests for **Towing, Flat Tires, Battery Jump-starts, Fuel Delivery,** and **Lockouts**. |
| **💳 Flexible Payments** | Supports both Cash and secure Cashless transactions with digital receipts. |
| **📶 Offline Mode** | Access cached maps and emergency contacts even with unstable internet connectivity. |
| **🆘 Emergency SOS** | Instantly alerts emergency contacts and local authorities with your precise GPS coordinates. |
| **🗣️ Multilingual** | Full support for **English** and **Tagalog**. |

### 🔧 For Service Providers
* **📋 Job Management:** Receive real-time alerts for nearby requests and toggle availability.
* **🗺️ Route Optimization:** Integrated navigation to find the fastest route to the client.
* **⭐ Reputation System:** Build trust through user reviews and ratings.

---

## 🔄 How It Works
1.  **Request:** The motorist selects a service (e.g., Towing) and confirms their location.
2.  **Matching:** The app queries the Firebase backend to find the nearest available Provider.
3.  **Acceptance:** The Provider receives a notification and accepts the job.
4.  **Tracking:** The Motorist tracks the Provider in real-time on the map.
5.  **Completion:** Service is rendered, payment is processed, and both parties rate the experience.

---

## 🏗️ Tech Stack

This project follows a **Native Android** architecture with a **Serverless** backend.

| Category | Technologies |
| :--- | :--- |
| **Frontend** | Java, XML, Android Studio |
| **Design** | Figma (Material Design), Room Database (Offline Cache) |
| **Backend** | Google Firebase (Auth, Firestore NoSQL, Realtime DB) |
| **Serverless** | Cloud Functions for Firebase |
| **APIs** | Google Maps SDK, Fused Location Provider, Retrofit |
| **Security** | AES-256 Encryption (User Data), OAuth 2.0 |

---

## 📱 Screenshots

| Splash Screen | Home Dashboard | Service Tracking |
|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/f1608b0f-6132-474d-a1cd-a9716dcc1475" width="250"> | <img src="https://github.com/user-attachments/assets/444d4a15-be6f-4aa6-bf7c-89efd3d92ae5" width="250"> | <img src="https://github.com/user-attachments/assets/9072d41b-de61-46f0-9e60-240f56969fff" width="250"> |

---

### Prerequisites
* Android Studio (Latest Version)
* Java Development Kit (JDK) 11+
* A Google Firebase Project


## 👥 Authors

* **Jemimah C. Sumague** - *Researcher / QA Tester*
* **Joshua Alnie P. Rio** - *Lead Developer (Frontend & Backend)*
* **Winnely Mae Anne A. Espinas** - *Project Manager / UI/UX Designer*

---

## 📄 License
This project is a Capstone Project submitted to **STI College Tanauan**.
*Developed as part of the Bachelor of Science in Information Technology program (2025).*
