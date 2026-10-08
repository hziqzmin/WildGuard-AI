## WildGuard AI

**WildGuard AI** is an experimental Android application that runs lightweight Large Language Models (LLMs) locally on-device using MediaPipe and Jetpack Compose.

---

Model Download:  
This app requires the quantized MediaPipe task bundle to run locally:

* **Model:** [AfiOne/gemma3-1b-it-int4.task](https://huggingface.co/AfiOne/gemma3-1b-it-int4.task)
* **Format:** MediaPipe LLM Inference Task (`.task`)
* **Quantization:** 4-bit integer (`int4`)

---

## 🚀 Getting Started

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

Disclaimer:
This project was built primarily through trial and error as an exploration of edge-device AI. Output quality, generation latency, and resource usage will vary significantly across hardware configurations.  

There are still a lot to improve in terms of the quality of the text output. I plan to use a better on-device AI model while not consuming a lot of the device battery consumption. The mobile app UI/UX also need to be improved a lot since the current version is only using the default Jetpack Compose template.
