# Connect Hearo to GitHub and push

Your project already has Git initialized on branch `main`. Follow these steps to push it to GitHub.

---

## Step 1: Create a new repository on GitHub

1. Open [github.com](https://github.com) and sign in.
2. Click the **+** (top right) → **New repository**.
3. Set:
   - **Repository name:** `Hearo` (or e.g. `hearo-android`).
   - **Description:** optional (e.g. "Android app for Hearo").
   - **Visibility:** Private or Public.
   - **Do not** check "Add a README", "Add .gitignore", or "Choose a license" — the project already has these.
4. Click **Create repository**.

GitHub will show a page with setup commands. You only need the **repository URL**, e.g.:

- HTTPS: `https://github.com/YOUR_USERNAME/Hearo.git`
- SSH: `git@github.com:YOUR_USERNAME/Hearo.git`

---

## Step 2: Add the GitHub remote (in terminal or Android Studio)

**Option A – Terminal (in project root):**

```bash
cd /Users/henkro/AndroidStudioProjects/Hearo
git remote add origin https://github.com/YOUR_USERNAME/Hearo.git
```

Replace `YOUR_USERNAME` and `Hearo` with your GitHub username and repo name. Use the SSH URL if you use SSH keys.

**Option B – Android Studio:**

1. **VCS → Git → Remotes…** (or **Git → Manage Remotes…**).
2. Click **+**, name: `origin`, URL: your repo URL from Step 1.
3. **OK**.

---

## Step 3: Commit all current files (if not already done)

If you haven’t made the first commit yet:

**Terminal:**

```bash
git add -A
git status   # optional: check what will be committed
git commit -m "Initial commit: Hearo Android app"
```

**Android Studio:**

1. **VCS → Commit** (or **Git → Commit**).
2. Select all changes, enter message: `Initial commit: Hearo Android app`.
3. **Commit**.

---

## Step 4: Push to GitHub

**Terminal:**

```bash
git push -u origin main
```

**Android Studio:**

1. **VCS → Git → Push** (or **Git → Push**).
2. If prompted, set **remote** to `origin` and **branch** to `main`.
3. **Push**.

If you use HTTPS, GitHub may ask for a **Personal Access Token** instead of your password. Create one under: **GitHub → Settings → Developer settings → Personal access tokens**.

---

## Step 5: Confirm on GitHub

Refresh the repository page on GitHub. You should see your project files and the first commit.

---

## Summary checklist

- [ ] Create new repo on GitHub (no README/.gitignore/license).
- [ ] Add remote: `git remote add origin <your-repo-url>`.
- [ ] Commit: `git add -A` then `git commit -m "Initial commit: Hearo Android app"`.
- [ ] Push: `git push -u origin main`.
- [ ] Optionally: enable GitHub in Android Studio via **File → Settings → Version Control → GitHub** and sign in.

`app/release/` is in `.gitignore` so built APKs are not pushed.
