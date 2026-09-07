## June 11, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 13, 2026

- Added Meta SDK Dependencies
- Import Router and FastAPI to backend

Changes by:josephiii

## June 14, 2026

- Implemented complete open source Kotlin application

Changes by:LucSal6868

### June 16, 2026

- Change in Gradle build files to resolve merge conflict

Changes by:Jaurelus

## June 18, 2026

- Changes to resolve merge conflcits with test app and backend

Changes by:Jaurelus

## June 19, 2026

- Register and login schema created
- /register endpoint skeleton created
- /login endpoint skeleton created
- Use brcypt to hash and decode passwords

Changes by:josephiii

### June 19, 2026

- [No code changed, but a commit was made]

Changes by:AMDev80

# June 25, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

### June 28, 2026

- Documentation added

Changes by:AMDev80

## June 28, 2026

- OkHTTP for login and register API calls
- Frontened skeleton for login and register page

Changes by:Jaurelus

## June 30, 2026

- Modified the `.github/workflows/summarizer.yaml` file to include `fetch-depth: 2` in the `Get Repo` step to increase the git fetch depth.
- Updated the `Get diff from repo` step to use `git diff $(git hash-object -t tree /dev/null) HEAD` instead of `git diff --root HEAD` to get the diff from the initial commit when `HEAD~1` is not available.
- No changes were made to the `Call AI API to summarize diff` step.

Changes by:AMDev80

## June 30, 2026

- Modified the GitHub workflow file `summarizer.yaml` to ignore pushes to the `main` branch by adding `branches-ignore` with the value `- main`.

Changes by:Jaurelus

## July 04, 2026

- Refactored the `register` method in `AuthService` to directly use the Supabase client for user registration, removing the need for manual password hashing and database interactions. The method now takes in `username`, `email`, and `password` as separate parameters.
- Refactored the `login` method in `AuthService` to use the Supabase client for user authentication, removing the need for manual password verification and database interactions. The method now takes in `email` and `password` as separate parameters.
- Removed the use of `bcrypt` for password hashing and verification, as well as the `RegisterSchema` and `LoginSchema` from the `schemas.AuthSchema` module.
- Removed TODO comments related to checking for existing users, adding password requirements, and returning a user object with a JWT, as these are now handled by the Supabase client.

Changes by:josephiii

## July 05, 2026

- Updated `requirements.txt` to include the `torch` library, in addition to existing dependencies such as `uvicorn`, `python-dotenv`, `bcrypt`, `pyjwt`, and `pydantic[email]`.
- Added a new image file `images.jpg` to the `vision-language-models` directory.
- Removed the `manager.py` file from the `vision-language-models` directory.
- Created a new `model_class.py` file, defining a `VLModel` class with methods for loading, unloading, and describing images.
- Created a new `model_manager.py` file, although it currently contains only an import statement and no functional code.
- Created a new `model_qwen2-vl.py` file, implementing a `Qwen2_VL` class that inherits from `VLModel` and utilizes the `Qwen2VLForConditionalGeneration` model and `AutoProcessor` from the `transformers` library to describe images.
- Implemented the `load_model` method to load the `Qwen2VLForConditionalGeneration` model and `AutoProcessor`, and the `describe_image` method to generate a description of an image using the loaded model and processor.

Changes by:LucSal6868

## July 06, 2026

- [No code changed, but a commit was made]

Changes by:LucSal6868

## July 06, 2026

- [No code changed, but a commit was made]

Changes by:LucSal6868

### July 08, 2026

- The title of the FastAPI application has been updated from "MIRA" to "VISIOR" in `backend/main.py`.
- Routes for authentication have been updated in `backend/main.py` and `backend/routes/AuthRoutes.py`, with new route prefixes and tags for organization.
- New routes for Vision Language Model (VLM) functionality have been added in `backend/routes/VLMRoutes.py`, including image prompt analysis.
- The structure of the project has been updated, including the addition of `services/VLMService.py` for VLM logic and `schemas/VLMSchema.py` for defining schema.
- The `AuthService` and `VLMService` classes have been created to encapsulate authentication and VLM logic, respectively.
- New models have been added, including `Qwen2_VL`, which utilizes the Qwen2VLForConditionalGeneration model and processor.
- Various changes have been made to `model_qwen2_vl.py`, including the update of the device map to use CPU instead of auto.
- Binary files and cache files have been updated, including `__pycache__` files for Python compiled code.
- A new Vision Language Model service has been implemented to handle image analysis with the help of the Qwen2 VL model.
- Added a route for posting an image prompt in `backend/routes/VLMRoutes.py`.

Changes by:LucSal6868

## July 08, 2026

- Added `backend/**/__pycache__/` to `.gitignore` to prevent caching issues with Python files
- Updated `README.md` to reflect the new project name "VISOR" and added getting started instructions
- Added new files `documentation/research/depth-model-research.md` and `documentation/research/server-hosting-research` to document research on depth modeling and server hosting
- Updated `documentation/research/server-hosting-research` to outline the overview, problem, and solution for server hosting, including the decision to use AWS and EC2, instance types, memory, latency, and pricing considerations.

Changes by:josephiii

## July 08, 2026

- Added `backend/**/__pycache__/` to `.gitignore` to prevent caching issues with Python files
- Updated `README.md` to reflect the new project name "VISOR" and added getting started instructions
- Refactored the `register` and `login` methods in `AuthService` to directly use the Supabase client for user registration and authentication
- Updated `AuthRoutes` to handle registration and login with the new `AuthService` methods
- Added new files `documentation/research/depth-model-research.md` and `documentation/research/server-hosting-research` to document research on depth modeling and server hosting
- Created `Database.py` to handle database interactions with Supabase
- Updated `main.py` to include the new `Database.py` file
- Updated `requirements.txt` to include the Supabase library and remove unnecessary dependencies
- Added `LoginScreen` and `RegisterScreen` to the mobile app to handle user authentication
- Updated the mobile app's `build.gradle.kts` file to include new dependencies for Compose Tooling and Preview Support
- Updated the `gradle/libs.versions.toml` file to include new versions for Compose UI Tooling and Preview libraries

Changes by:LucSal6868

## July 09, 2026

- Added error handling to the `register` method in `AuthService` to catch any exceptions that may occur during the sign-up process and raise an `HTTPException` with a 400 status code and a "Registration Failed" detail.
- Added error handling to the `login` method in `AuthService` to catch any exceptions that may occur during the sign-in process and raise an `HTTPException` with a 401 status code and an "Invalid email or password" detail.
- Created a new method `delete_account` in `AuthService` that takes a token as input, retrieves the user associated with the token, and deletes the user's account using the `admin.delete_user` method from the Supabase client. The method also includes error handling to catch any exceptions that may occur during the deletion process and raises an `HTTPException` with a 401 status code and an "Invalid or expired user token" detail if the token is invalid or expired.
- Removed the `email` and `password` parameters from the `delete_account` method as they are not needed, and instead use the provided `token` to authenticate the user.

Changes by:josephiii

## July 09, 2026

- Modified the `.gitignore` file to include `__pycache__/` and `*.py[cod]` to ignore Python bytecode files
- Updated the `settings.json` file in the `.vscode` directory to set the default Python environment manager to `ms-python.python:system`
- Added error handling to the `register` method in `AuthService` to catch any exceptions that may occur during the sign-up process and raise an `HTTPException` with a 400 status code and a "Registration Failed" detail
- Added error handling to the `login` method in `AuthService` to catch any exceptions that may occur during the sign-in process and raise an `HTTPException` with a 401 status code and an "Invalid email or password" detail
- Created a new method `delete_account` in `AuthService` that takes a token as input, retrieves the user associated with the token, and deletes the user's account using the `admin.delete_user` method from the Supabase client
- Updated the `AuthRoutes` to include a new endpoint `/deleteAccount` that calls the `delete_account` method from `AuthService`
- Added new dependencies to the `requirements.txt` file, including `numpy`, `transformers`, `accelerate`, `pillow`, `torchvision`, and `python-multipart`

Changes by:josephiii
## July 10, 2026
* Updated the `.github/workflows/summarizer.yaml` file to modify the prompt for the AI model, removing the phrase "At the end of each entry" to improve the clarity of the instructions.
* Modified the `Add the author` step in the workflow file to add a newline character after the author's name.
* No other code changes were made, but the workflow file was updated to reflect the changes in the instructions.

Changes by:josephiii

## July 14, 2026
* Added the Google ML Kit Text Recognition library to the project by including `com.google.mlkit:text-recognition:16.0.1` in the `build.gradle.kts` file.
* Created a new class `TextReaderOCR` in `com.meta.wearable.dat.externalsampleapps.cameraaccess.ocr` package, which provides a method `readText` to recognize text from a bitmap image using the Google ML Kit Text Recognition library.
* Added a new class `Speaker` in `com.meta.wearable.dat.externalsampleapps.cameraaccess.tts` package, which provides methods to speak text using the Android Text-to-Speech (TTS) API.
* Modified the `MainActivity` class to include instances of `TextReaderOCR` and `Speaker`, and to initialize and shut down the `Speaker` instance in the `onStart` and `onDestroy` methods respectively.
* Implemented the `onDestroy` method in `MainActivity` to close the `TextReaderOCR` instance and shut down the `Speaker` instance when the activity is destroyed.

Changes by:josephiii

## July 14, 2026
* Modified the `.gitignore` file to include new ignore rules for Python, virtual environments, and cache directories, and removed some unnecessary ignores.
* Updated the `backend/requirements.txt` file to add new dependencies, including `torch`, `torchvision`, `transformers`, `accelerate`, `pillow`, `qwen-vl-utils`, and `einops`.
* Introduced a new class `Gemma` in `backend/services/vision_language_models/model_gemma.py`, which inherits from the `VLModel` class and uses the `AutoModelForImageTextToText` model from the `transformers` library.
* Introduced a new class `Moondream` in `backend/services/vision_language_models/model_moondream.py`, which inherits from the `VLModel` class and uses the `AutoModelForCausalLM` model from the `transformers` library.
* Introduced a new class `QwenVL` in `backend/services/vision_language_models/model_qwen.py`, which inherits from the `VLModel` class and uses the `AutoModelForImageTextToText` model from the `transformers` library.
* Modified the `model_manager.py` file to import and use the new `Gemma` model instead of the old `Qwen2_VL` model.
* Removed the `model_qwen2_vl.py` file as it is no longer needed.

Changes by:josephiii

## July 14, 2026
* Added a new implementation for the ProfileCreationScreen, which guides the user through a series of questions to help them set up their profile.
* Implemented a new ProfileFlowHost composable function, which hosts the profile creation wizard and persists the result.
* Added a new ProfileStore class, which handles local, on-device profile storage.
* Implemented a new SettingsScreen, which allows users to modify their profile settings.
* Added a new UserProfile data model, which represents the user's profile data.
* Introduced a new VisorPalette class, which defines accessibility-first color palettes for the app. 
* Added new dependencies to the build.gradle.kts file, including androidx.compose.ui:ui-tooling-preview and androidx.compose.ui:ui-tooling.

Changes by:AMDev80

## July 17, 2026
* Changed the model implementation in `VLMService.py` from `Qwen2_VL` to `QwenVL` to utilize a potentially more accurate vision language model.
* Updated the model selection in `model_manager.py` to use `QwenVL` for testing purposes.
* Modified the device map in `model_qwen.py` to utilize the CPU instead of automatic device mapping for the `QwenVL` model, potentially improving inference performance.
* Added a new documentation file `VLM.md` to provide an overview of the vision language model, its components, and testing procedures.
* Updated `AndroidManifest.xml` to allow cleartext traffic, which may be necessary for certain API requests, but should be removed in production.
* Modified `MainActivity.kt` to comment out the `CameraAccessScaffold` and uncomment the `RegisterScreen`, potentially changing the default startup screen.
* Changed the image compression format and quality in `SharePhotoDialog.kt` from JPEG with 80% quality to JPEG with 90% quality, potentially improving image quality.
* Updated the API request in `SharePhotoDialog.kt` to use a multipart body with a JPEG image and a prompt, potentially simplifying the request process and improving performance.

Changes by:LucSal6868

## July 21, 2026
* Added user authentication to the `answer_question` method in `VLMService.py` using Supabase client, which checks if a user exists and raises an HTTP exception if the token is invalid or expired.
* Modified the `answer_question` method to accept a token, prompt, and image bytes, and to use a temporary file for the image.
* Updated the `model_manager.py` file to test the `describe_image` method with different images and prompts.
* Added new images to the `vision_language_models/images` directory for testing purposes.
* Created a new file `VLM tests.md` in the `documentation/testing` directory to document simple VLM tests, including descriptions of images and expected model outputs.
* Added images to the `documentation/testing/images` directory to support the VLM tests documentation. 
* [No code changed, but a commit was made] for the following files: 
  - backend/services/vision_language_models/images/dangerous.jpg
  - backend/services/vision_language_models/images/dog.jpg
  - backend/services/vision_language_models/images/edge.jpg
  - backend/services/vision_language_models/images/fire.jpg
  - backend/services/vision_language_models/images/house.jpg
  - backend/services/vision_language_models/images/manhole.jpg
  - backend/services/vision_language_models/images/messy.jpg
  - backend/services/vision_language_models/images/night.jpg
  - backend/services/vision_language_models/images/playground.jpg
  - backend/services/vision_language_models/images/puppy.png
  - backend/services/vision_language_models/images/street.jpg
  - documentation/testing/images/Pasted image 20260721123005.png
  - documentation/testing/images/Pasted image 20260721123103.png
  - documentation/testing/images/Pasted image 20260721123112.png

Changes by:Jaurelus

null

Changes by:AMDev80

null

Changes by:AMDev80

null

Changes by:AMDev80

null

Changes by:AMDev80

null

Changes by:Jaurelus

null

Changes by:AMDev80

null

Changes by:LucSal6868

null

Changes by:LucSal6868

null

Changes by:LucSal6868

null

Changes by:AMDev80

null

Changes by:Jaurelus

