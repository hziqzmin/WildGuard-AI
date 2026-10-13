# WildGuard AI : On-Device Wilderness Survival Assistant

**A native Android application running local, quantized Small Language Models (SLMs) and on-device Retrieval-Augmented Generation (RAG) to provide real-time, life-saving survival guidance in zero-connectivity environments.**

---

## Problem Statement: The Disconnection Dilemma
Outdoor enthusiasts, hikers, and field researchers frequently venture into off-grid regions with zero cellular reception. In emergency scenarios (injuries, extreme weather, getting lost, gear failure), traditional search engines and cloud-based AI assistants (ChatGPT, Gemini) become completely inaccessible. 
**WildGuard AI solves this by bringing modern generative AI and curated survival information directly to the edge, functioning 100% offline in Airplane mode with zero cloud dependency.**

---

## Technical Architecture & On-Device RAG Pipeline
WildGuard AI utilizes an offline-first **Retrieval-Augmented Generation (RAG)** pipeline designed specifically around mobile hardware constraints (minimizing RAM usage and battery drain while eliminating hallucinations).

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        ON-DEVICE RAG PIPELINE                          │
└────────────────────────────────────────────────────────────────────────┘

               [ 👤 User Query: "How to treat hypothermia?" ]
                                     │
                                     ▼
      ┌─────────────────────────────────────────────────────────────┐
      │ 1. Vector Embedding (MediaPipe / TFLite ~5MB)               │
      │ • Converts short user query into high-dimensional vector    │
      └──────────────────────────────┬──────────────────────────────┘
                                     │
                                     ▼
      ┌─────────────────────────────────────────────────────────────┐
      │ 2. Hybrid Retrieval Scorer (< 10ms execution)               │
      │ • Title & Keyword matching (Direct topic alignment)         │
      │ • Cosine vector similarity against pre-computed vectors     │
      │   stored in knowledge_base.json                             │
      └──────────────────────────────┬──────────────────────────────┘
                                     │
                                     ▼
      ┌─────────────────────────────────────────────────────────────┐
      │ 3. Grounded Context Synthesis & Guardrail Prompting         │
      │ • Injects Top-K verified survival passages into strict      │
      │   anti-hallucination prompt templates                       │
      └──────────────────────────────┬──────────────────────────────┘
                                     │
                                     ▼
      ┌─────────────────────────────────────────────────────────────┐
      │ 4. Local SLM Inference (Gemma 3 1B INT4 / MediaPipe GenAI) │
      │ • GPU-accelerated inference with automatic CPU fallback     │
      │ • Real-time token streaming via asynchronous callbacks      │
      └──────────────────────────────┬──────────────────────────────┘
                                     │
                                     ▼
     [ 📱 Structured Actionable Response: Summary + Steps + Caution Alert ]
```
---


### 💡 Key Engineering Decisions in this Pipeline:
1. **Procedural (Semantic) Chunking Over Fixed-Size Chunking:**
   Survival information cannot be split arbitrarily across token counts (e.g., cutting a snakebite first aid steps in half is dangerous). Each chunk is intentionally structured around a complete problem-solution pair: topic name, concise explanation, numbered steps, and cautions.
2. **Pre-Computed Vectors for 0ms Mobile Latency:**
   By embedding the documents on the developer's workstation prior to compilation, the mobile device never has to compute embeddings for the knowledge base. Upon app launch, the Kotlin app simply loads the JSON and reads the raw float arrays into memory in under **10 milliseconds**, consuming less battery.
3. **OTA (Over-The-Air) Updatable Architecture:**
   Because `knowledge_base.json` is a decoupled data layer, the developer can ingest new wilderness manuals, re-run the Python script, and distribute updated knowledge files over-the-air via Firebase Storage/Remote Config without requiring users to download a new APK update from Google Play.
   
---
## Tech Stack & Technologies
* **Language:** Kotlin (100% Native Android)
* **UI Framework:** Jetpack Compose & Material 3 (Declarative UI)
* **Architecture:** MVVM / MVI with Kotlin Coroutines & StateFlow
* **On-Device AI Engine:** Google MediaPipe Tasks GenAI (`tasks-genai`)
* **Local Models:** Quantized Gemma 3 1B IT (4-bit integer / INT4 `.task` bundle)
* **Embeddings & Search:** TensorFlow Lite (`embedding_model.tflite`) + Cosine Similarity
* **Backend & Cloud Sync:** Firebase Authentication & Jetpack DataStore (Online sync + offline persistence)
* **Local Storage:** Android Internal Storage & Asset Management
---
## Active Development: What’s Coming in Version 2 (v2)
I am currently developing **Version 2** on the [`v2` branch](https://github.com/hziqzmin/WildGuard-AI/tree/v2), taking WildGuard AI from an experimental prototype to a production-grade application:
1. **Complete UI/UX Overhaul:**
   * Rebuilding the interface from scratch in Jetpack Compose using a custom **Tactical Wilderness** palette (`#2D3A3A`, `#F5F0E0`, `#FAFAFA`, `#26A69A`) and Montserrat typography.
   * Dynamic 2-state interface: Vertically centered prompt card on launch transitioning smoothly into a docked, conversational messaging interface.
   * Minimalist 5-icon bottom navigation bar (*Guides, Compass, AI Home, SOS, Profile*).
2. **AI & Battery Performance Optimizations:**
   * **Real-Time Token Streaming:** Upgrading from blocking synchronous generation to asynchronous token streaming (`generateResponseAsync`), rendering words live in < 0.5s.
   * **GPU Acceleration:** Offloading matrix multiplication to mobile GPUs to accelerate generation by 3x–5x and prevent sustained CPU thermal throttling.
   * **Lazy Loading & Auto-Unload:** Dynamically loading the 600MB model into RAM only during active chat and auto-releasing memory after 5 minutes of inactivity to save battery.
3. **Cloud & Hybrid Capabilities:**
   * Integrating **Firebase Authentication** for user accounts, profile syncing, and cloud-backed Emergency Medical IDs, fully paired with offline session caching for seamless off-grid usage.
   * Support for Over-The-Air (OTA) survival knowledge base updates.

---

## Model Download:  
This app requires the quantized MediaPipe task bundle to run locally:

* **Model:** [AfiOne/gemma3-1b-it-int4.task](https://huggingface.co/AfiOne/gemma3-1b-it-int4.task)
* **Format:** MediaPipe LLM Inference Task (`.task`)
* **Quantization:** 4-bit integer (`int4`)

---

## Getting Started

### Prerequisites
* Android device running **Android 8.0 (API level 26)** or higher
* Recommended: At least 4 GB–6 GB of free RAM on the device for smooth inference

### Setup Instructions
1. **Download the model bundle:**  
   Download `gemma3-1b-it-int4.task` from the Hugging Face link above.

2. **Place the model in the project assets:**  
   Copy the downloaded `.task` file into the app's assets folder:
   ```text
   app/src/main/assets/gemma3-1b-it-int4.task
   
3. Run the app.

---

## Disclaimer:  
This project was built primarily through trial and error as an exploration of edge-device AI. Output quality, generation latency, and resource usage will vary significantly across hardware configurations.  

There are still a lot to improve in terms of the quality of the text output. I plan to use a better on-device AI model while not consuming a lot of the device battery consumption. The mobile app UI/UX also need to be improved a lot since the current version is only using the default Jetpack Compose template.
