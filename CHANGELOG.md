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
