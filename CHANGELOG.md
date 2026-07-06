
## June 11, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 13, 2026
* [No code changed, but a commit was made]

Changes by:josephiii
## June 13, 2026
* [No code changed, but a commit was made]

Changes by:josephiii
### June 13, 2026
* [No code changed, but a commit was made]

Changes by:josephiii
### June 16, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
### June 16, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 18, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 18, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
### June 19, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
## June 19, 2026
* [No code changed, but a commit was made]

Changes by:josephiii
## June 19, 2026
* [No code changed, but a commit was made]

Changes by:josephiii
### June 19, 2026
* [No code changed, but a commit was made]

Changes by:AMDev80
# June 25, 2026
* [No code changed, but a commit was made]

Changes by:Jaurelus
### June 28, 2026
* [No code changed, but a commit was made]

Changes by:AMDev80
## June 28, 2026
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
