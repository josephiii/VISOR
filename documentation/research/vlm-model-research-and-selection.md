For our project, we plan to implement three core features: **text recognition**, **hazard detection**, and **scene description**. To achieve this, we researched various machine learning models to identify the best technologies for these tasks.

Since **scene description** is the most essential aspect of our application, our initial research focused heavily on identifying the AI models best suited for visual translation and environmental understanding.

### Computer Vision & Multimodal Models Evaluated

We analyzed six primary model types capable of processing image descriptions, object recognition, and scene analysis:

- **Vision Language Models (VLMs)**
    
    - _Capabilities:_ Understand images deeply and can answer open-ended questions about them. They are built on top of Large Language Models (LLMs), allowing them to process both text and images seamlessly.
        
    - _Limitations:_ High computational cost. They require significant hardware for deployment, suffer from longer inference times, and are susceptible to hallucinations.
        
- **Image Captioning**
    
    - _Capabilities:_ Generates text descriptions of a whole image.
        
    - _Limitations:_ Acts as a "one and done" model; it cannot answer follow-up questions, dive into granular details, or truly "understand" spatial context.
        
- **Dense Captioning**
    
    - _Capabilities:_ Describes multiple localized regions within a single image, allowing captions to overlap and interact.
        
    - _Limitations:_ Lacks deep contextual understanding; semantic relationships between separated objects may be completely ignored.
        
- **Object Detection**
    
    - _Capabilities:_ Locates and labels specific objects within an image using bounding boxes.
        
    - _Limitations:_ Strictly limited to its training classes. It does not understand actions, relationships, or object properties, and struggles heavily with occluded (partially hidden) objects.
        
- **Image Classification**
    
    - _Capabilities:_ Identifies the singular, dominant subject of an image to categorize it into a predefined class.
        
    - _Limitations:_ Too case-specific and narrow for navigating dynamic, real-world environments.
        
- **Scene Recognition**
    
    - _Capabilities:_ Identifies the overall environment type (e.g., "kitchen," "park," "street").
        
    - _Limitations:_ Too vague for asset navigation as it completely ignores individual objects and layout details.
        

### Core Candidate Comparison

Because our application is designed to help users with low vision navigate their surroundings, **utility** is our primary metric. The model must describe a scene with enough detail for a user to traverse an environment or locate an item.

However, we must balance this with **performance (latency)**; if a model takes too long to generate a caption, the user experience becomes frustrating or unsafe. While _Image Captioning_ and _Dense Captioning_ offered speed advantages, they lacked the contextual depth required for safe navigation.

Ultimately, we chose to utilize a **Vision Language Model (VLM)**. To mitigate the heavy computational costs, we focused on lightweight, sub-2B parameter models designed for lower-end hardware. We narrowed our choices down to two specific models:

|**Feature**|**Moondream**|**Qwen2-VL (Selected)**|
|---|---|---|
|**Primary Strength**|Extremely lightweight; highly optimized for low-power devices.|High accuracy, multi-size scalability, and excellent relationship reasoning.|
|**Parameter Sizes**|< 2B parameters (Fixed)|2B, 7B, and 72B variants (Scalable via simple Model ID changes).|
|**Navigation Utility**|Basic scene understanding and question answering.|Deep spatial awareness; maps relative object locations and environmental hazards.|
|**Deployment**|Excellent local deployment; minimal inference latency.|Higher computational cost; requires cloud hosting or mobile optimization.|

### Final Selection Justification: Qwen2-VL

In the end, we selected **Qwen2-VL** as our core model for the following critical reasons:

1. **Safety & Accuracy:** Qwen2-VL is significantly more precise and less likely to fail or hallucinate when describing surroundings. Because visually impaired users may rely on this application to actively avoid hazards, accuracy must take priority over local hardware constraints.
    
2. **Scalability:** Qwen2-VL allows us to seamlessly upgrade our architecture. We can develop our proof-of-concept using the lightweight **2B parameter model**, and seamlessly scale up to the 7B or 72B models in Python later if our feature set demands it.
    
3. **Relational Awareness:** Unlike Moondream, Qwen excels at understanding how objects interact spatially. It can deduce where objects are relative to one another and isolate text or signage, which directly fulfills our goals for hazard detection and text recognition.
    

### Initial Prototyping & Technical Hurdling

During local implementation utilizing PyTorch, we encountered hardware compatibility barriers. Qwen2-VL is highly optimized for parallel processing on dedicated Nvidia or Apple Silicon GPUs; because my development machine utilizes an **integrated iGPU**, the model was forced to run entirely on the CPU. This resulted in unviable, massive inference delays.

To bypass this roadblock during the prototyping phase, we moved development to **Google Colab** to leverage remote cloud GPUs for free.

#### Verification Test Case

Using the **Qwen2-VL-2B** variant, we initiated a baseline test using a standard assistant prompt template to analyze a colorful children's playground.

- **System Prompt:** `You are a helpful assistant.`
    
- **User Prompt:** `What is in this image?`
    
- **Model Output:** > "The image shows a colorful playground with various slides and structures. The playground is surrounded by trees and a clear blue sky. There is a picnic table and benches nearby, indicating that the area is designed for relaxation and leisure."
    

The test was highly successful. The model not only cataloged the physical structures accurately but successfully inferred the semantic utility of the environment (noting the area was "designed for leisure"). Future iterations will involve fine-tuning prompts to force specialized spatial outputs (e.g., instructing the model to report items relative to the user's field of view using _left_, _right_, or _center_ indicators).