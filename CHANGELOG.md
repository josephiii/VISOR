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
## July 02, 2026
* Updated the `.gitignore` file to ignore `backend/**/__pycache__/` instead of `backend/__pycache__/` to handle nested directories.
* Added new files to the `documentation/research` directory: `depth-model-research.md` and `server-hosting-research`.
* The `server-hosting-research` file outlines the research and decision-making process for choosing a server hosting platform for the VISOR project, including the selection of AWS EC2 as the hosting platform, and the evaluation of different instance types (G, P, TRN, DL, and INF) to determine the best fit for the project's needs, with a focus on balancing performance, cost, and energy consumption.
* The research also considers factors such as memory requirements, latency, and pricing for the selected instance types, including the G4, G5, and G6 instances, and their respective NVIDIA chips (T4, A10G, and L4).
* The conclusion of the research suggests that the g6e.xlarge and g6.xlarge instances are the most suitable production options for running the project, taking into account the need for a larger LLM and the importance of balancing performance and cost. 
* [No code changed, but a commit was made]

Changes by:Jaurelus
## July 02, 2026
* Updated the `.gitignore` file to ignore `backend/**/__pycache__/` instead of `backend/__pycache__/` to handle nested directories.
* Added new files to the `documentation/research` directory: `depth-model-research.md` and `server-hosting-research`.
* Created the `server-hosting-research` file to document the research and decision-making process for choosing a server hosting platform, including the evaluation of AWS EC2 instance types (G, P, TRN, DL, and INF) and the selection of the g6e.xlarge and g6.xlarge instances as the most suitable production options.
* Researched and documented factors such as memory requirements, latency, and pricing for the selected instance types, including the G4, G5, and G6 instances, and their respective NVIDIA chips (T4, A10G, and L4).
* [No code changed, but a commit was made]

Changes by:Jaurelus
## July 02, 2026
* Added new file `DepthEstRoutes.py` to `backend/routes` directory.
* Added new file `DepthEstSchema.py` to `backend/schemas` directory.
* Added new file `DepthEstService.py` to `backend/services` directory, which includes the following functions:
  + `resizeImage()`: resizes images to 518px to optimize model performance.
  + `compressImageQuality()`: currently empty, intended to compress image quality.
  + `runModel()`: currently empty, intended to run a model.
  + `returnAudio()`: currently empty, intended to return audio.

Changes by:Jaurelus
## July 09, 2026
* Refactored the `Depth` class to include a `chooseDevice` method that determines the device type to run the model in, either GPU (cuda), Apple Silicon (mps), or CPU.
* Added an `__init__` method to the `Depth` class that loads the pre-trained model and image processor once, improving efficiency.
* Modified the `runModel` method to use the pre-loaded model and image processor, and to run the inference part of the model on the chosen device.
* Changed the `compressImage` method to be an instance method, but the implementation remains the same.
* Removed unnecessary code and improved error handling in the `runModel` method.
* Updated the model and image processor to use the `torch_dtype` and `device` attributes for more efficient processing.

Changes by:Jaurelus
