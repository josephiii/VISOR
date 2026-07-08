## June 11, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 13, 2026

- [No code changed, but a commit was made]

Changes by:josephiii

## June 13, 2026

- [No code changed, but a commit was made]

Changes by:josephiii

### June 13, 2026

- [No code changed, but a commit was made]

Changes by:josephiii

### June 16, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

### June 16, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 18, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 18, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

### June 19, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 19, 2026

- [No code changed, but a commit was made]

Changes by:josephiii

## June 19, 2026

- [No code changed, but a commit was made]

Changes by:josephiii

### June 19, 2026

- [No code changed, but a commit was made]

Changes by:AMDev80

# June 25, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

### June 28, 2026

- [No code changed, but a commit was made]

Changes by:AMDev80

## June 28, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

## June 28, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus

### June 19, 2026

- [No code changed, but a commit was made]

Changes by:AMDev80

### June 27, 2026

- [No code changed, but a commit was made]

Changes by:Jaurelus
### June 28, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 29, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 29, 2026
* Modified the `.github/workflows/summarizer.yaml` file to include `fetch-depth: 2` in the `Get Repo` step to increase the git fetch depth.
* Updated the `Get diff from repo` step to use `git diff $(git hash-object -t tree /dev/null) HEAD` instead of `git diff --root HEAD` to get the diff from the initial commit when `HEAD~1` is not available.
* No changes were made to the `Call AI API to summarize diff` step.

Changes by:Jaurelus
## June 30, 2026
* Modified the `.github/workflows/summarizer.yaml` file to include `fetch-depth: 2` in the `Get Repo` step to increase the git fetch depth.
* Updated the `Get diff from repo` step to use `git diff $(git hash-object -t tree /dev/null) HEAD` instead of `git diff --root HEAD` to get the diff from the initial commit when `HEAD~1` is not available.
* No changes were made to the `Call AI API to summarize diff` step.

Changes by:AMDev80
## June 30, 2026
* Modified the GitHub workflow file `summarizer.yaml` to ignore pushes to the `main` branch by adding `branches-ignore` with the value `- main`.

Changes by:Jaurelus
## July 05, 2026
* Updated `requirements.txt` to include the `torch` library, in addition to existing dependencies such as `uvicorn`, `python-dotenv`, `bcrypt`, `pyjwt`, and `pydantic[email]`.
* Added a new image file `images.jpg` to the `vision-language-models` directory.
* Removed the `manager.py` file from the `vision-language-models` directory.
* Created a new `model_class.py` file, defining a `VLModel` class with methods for loading, unloading, and describing images.
* Created a new `model_manager.py` file, although it currently contains only an import statement and no functional code.
* Created a new `model_qwen2-vl.py` file, implementing a `Qwen2_VL` class that inherits from `VLModel` and utilizes the `Qwen2VLForConditionalGeneration` model and `AutoProcessor` from the `transformers` library to describe images.
* Implemented the `load_model` method to load the `Qwen2VLForConditionalGeneration` model and `AutoProcessor`, and the `describe_image` method to generate a description of an image using the loaded model and processor.

Changes by:LucSal6868
## July 06, 2026
* [No code changed, but a commit was made]

Changes by:LucSal6868
## July 06, 2026
* [No code changed, but a commit was made]

Changes by:LucSal6868
### July 08, 2026
* The title of the FastAPI application has been updated from "MIRA" to "VISIOR" in `backend/main.py`.
* Routes for authentication have been updated in `backend/main.py` and `backend/routes/AuthRoutes.py`, with new route prefixes and tags for organization.
* New routes for Vision Language Model (VLM) functionality have been added in `backend/routes/VLMRoutes.py`, including image prompt analysis.
* The structure of the project has been updated, including the addition of `services/VLMService.py` for VLM logic and `schemas/VLMSchema.py` for defining schema.
* The `AuthService` and `VLMService` classes have been created to encapsulate authentication and VLM logic, respectively.
* New models have been added, including `Qwen2_VL`, which utilizes the Qwen2VLForConditionalGeneration model and processor.
* Various changes have been made to `model_qwen2_vl.py`, including the update of the device map to use CPU instead of auto.
* Binary files and cache files have been updated, including `__pycache__` files for Python compiled code. 
* A new Vision Language Model service has been implemented to handle image analysis with the help of the Qwen2 VL model. 
* Added a route for posting an image prompt in `backend/routes/VLMRoutes.py`.

Changes by:LucSal6868
## July 01, 2026
* Modified the `.github/workflows/summarizer.yaml` file to include `fetch-depth: 2` in the `Get Repo` step to increase the git fetch depth.
* Updated the `Get diff from repo` step to use `git diff $(git hash-object -t tree /dev/null) HEAD` instead of `git diff --root HEAD` to get the diff from the initial commit when `HEAD~1` is not available.
* Added `branches-ignore` to the workflow file to ignore pushes to the `main` branch.

Changes by:Jaurelus
## July 04, 2026
* Refactored the `register` method in `AuthService` to directly use the Supabase client for user registration, removing the need for manual password hashing and database interactions. The method now takes in `username`, `email`, and `password` as separate parameters.
* Refactored the `login` method in `AuthService` to use the Supabase client for user authentication, removing the need for manual password verification and database interactions. The method now takes in `email` and `password` as separate parameters.
* Removed the use of `bcrypt` for password hashing and verification, as well as the `RegisterSchema` and `LoginSchema` from the `schemas.AuthSchema` module.
* Removed TODO comments related to checking for existing users, adding password requirements, and returning a user object with a JWT, as these are now handled by the Supabase client.

Changes by:josephiii
## July 08, 2026
* Added `backend/**/__pycache__/` to `.gitignore` to prevent caching issues with Python files
* Updated `README.md` to reflect the new project name "VISOR" and added getting started instructions
* Added new files `documentation/research/depth-model-research.md` and `documentation/research/server-hosting-research` to document research on depth modeling and server hosting
* Updated `documentation/research/server-hosting-research` to outline the overview, problem, and solution for server hosting, including the decision to use AWS and EC2, instance types, memory, latency, and pricing considerations.

Changes by:josephiii
## July 08, 2026
* Added `backend/**/__pycache__/` to `.gitignore` to prevent caching issues with Python files
* Updated `README.md` to reflect the new project name "VISOR" and added getting started instructions
* Refactored the `register` and `login` methods in `AuthService` to directly use the Supabase client for user registration and authentication
* Updated `AuthRoutes` to handle registration and login with the new `AuthService` methods
* Added new files `documentation/research/depth-model-research.md` and `documentation/research/server-hosting-research` to document research on depth modeling and server hosting
* Created `Database.py` to handle database interactions with Supabase
* Updated `main.py` to include the new `Database.py` file
* Updated `requirements.txt` to include the Supabase library and remove unnecessary dependencies
* Added `LoginScreen` and `RegisterScreen` to the mobile app to handle user authentication
* Updated the mobile app's `build.gradle.kts` file to include new dependencies for Compose Tooling and Preview Support
* Updated the `gradle/libs.versions.toml` file to include new versions for Compose UI Tooling and Preview libraries

Changes by:LucSal6868
