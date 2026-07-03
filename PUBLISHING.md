# Publishing mdBook Viewer to the JetBrains Marketplace

This is the release runbook for this plugin. It assumes the publishing scaffolding already
in the repo: `build.gradle.kts` has `signing {}` + `publishing {}` blocks that read secrets
from environment variables, `build.yml` drafts a GitHub Release on each push to `main`, and
`release.yml` runs `./gradlew publishPlugin` when a Release is published.

Plugin facts:

- **ID:** `com.github.nbugash.mdbookviewerplugin` (must stay globally unique on Marketplace)
- **Name:** `mdBook Viewer`
- **Version:** set by `version` in `gradle.properties` (currently `0.0.1`)
- **Distribution artifact:** `build/distributions/mdbook-viewer-plugin-<version>.zip`

> **The golden rule:** the **first** version of a new plugin **must be uploaded manually**
> through the Marketplace website. The Gradle/API `publishPlugin` path only works for
> *updates* to an already-listed plugin. New listings also go through **moderation**
> (typically 1–2 business days) before they are public.

---

## 1. One-time setup

### 1.1 Finalize plugin metadata

Before the first upload, tidy `src/main/resources/META-INF/plugin.xml`:

- [ ] **Vendor email** — currently only a `url` is set. Marketplace reviewers expect a contact:
      `<vendor url="https://github.com/ArchonGridStudio" email="you@example.com">nbugash</vendor>`
- [x] **Description** — already a meaningful HTML block (Marketplace requires ≥ ~40 chars).
- [x] **Plugin icon** — `META-INF/pluginIcon.svg` is present.
- [x] **Compatibility** — `since-build = 252`, no upper bound (set in `build.gradle.kts`).
- [ ] **License** — pick one at upload time; add a `LICENSE` file to the repo if open source.
- [ ] **Version** — bump `version` in `gradle.properties` for every release (you cannot
      re-upload the same version number).

### 1.2 Create a Marketplace vendor account

1. Sign in at <https://plugins.jetbrains.com> with your JetBrains Account.
2. Open your profile and create a **vendor** profile (name, contact). Plugins are published
   under a vendor.

### 1.3 Generate a Marketplace permanent token (`PUBLISH_TOKEN`)

1. <https://plugins.jetbrains.com> → your profile → **My Tokens**.
2. Generate a **permanent token**, name it (e.g. `mdbook-viewer-ci`), and copy it. This is
   your `PUBLISH_TOKEN`. Store it somewhere safe — it is shown only once.

### 1.4 Generate a signing certificate + key

Marketplace verifies plugin signatures. Generate a self-signed chain and a password-protected
key (from the [plugin-signing docs](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)):

```bash
# 1) A password-protected RSA private key (you'll be prompted for a passphrase)
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096

# 2) Convert to PKCS#8 (prompts to decrypt, then to set a new passphrase — remember it)
openssl pkcs8 -topk8 -inform PEM -outform PEM -in private_encrypted.pem -out private.pem

# 3) A self-signed certificate chain valid for 10 years
openssl req -key private.pem -new -x509 -days 3650 -out chain.crt
```

This yields:

| File / value            | Maps to env var / secret  |
|-------------------------|---------------------------|
| `chain.crt` contents    | `CERTIFICATE_CHAIN`       |
| `private.pem` contents  | `PRIVATE_KEY`             |
| the PKCS#8 passphrase   | `PRIVATE_KEY_PASSWORD`    |

Keep `private.pem` and the passphrase secret. Do **not** commit them.

### 1.5 Add the four secrets to GitHub (for automated releases)

Repo → **Settings → Secrets and variables → Actions → New repository secret**, add all four:

- `PUBLISH_TOKEN` — the Marketplace token from 1.3
- `CERTIFICATE_CHAIN` — the full contents of `chain.crt` (include the `BEGIN/END` lines)
- `PRIVATE_KEY` — the full contents of `private.pem` (include the `BEGIN/END` lines)
- `PRIVATE_KEY_PASSWORD` — the PKCS#8 passphrase from 1.4

---

## 2. Build and verify locally

```bash
./gradlew clean check verifyPlugin buildPlugin
```

- `check` runs the unit tests.
- `verifyPlugin` runs the IntelliJ Plugin Verifier against the configured IDE range.
- `buildPlugin` produces `build/distributions/mdbook-viewer-plugin-<version>.zip`.

Optionally test signing locally (needs the env vars from 1.4 exported):

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="your-passphrase"
./gradlew signPlugin
# signed artifact -> build/distributions/*-signed.zip
```

---

## 3. First release — manual upload (required)

1. Build the ZIP: `./gradlew buildPlugin`.
2. Go to <https://plugins.jetbrains.com/plugin/add> (or **Upload plugin** from your profile).
3. Upload `build/distributions/mdbook-viewer-plugin-<version>.zip`.
4. Choose a **license**, a **category** (e.g. *Tools Integration*), and tags.
5. Submit. The plugin enters **moderation**; once approved it becomes public and its
   Marketplace page + plugin ID are live.

After this first approval, all later versions can be published automatically (Section 4).

---

## 4. Subsequent releases

### Option A — automated via GitHub Releases (recommended; already wired)

1. Bump `version` in `gradle.properties` and move your notes into the `[Unreleased]`
   section of `CHANGELOG.md`.
2. Commit and push to `main`. The **Build** workflow (`build.yml`) runs and creates a
   **draft GitHub Release** for that version, with the changelog notes.
3. Go to the repo's **Releases**, open the draft, review it, and **Publish** it.
4. Publishing the release triggers the **Release** workflow (`release.yml`), which:
   - signs the plugin and runs `./gradlew publishPlugin` (using the four secrets),
   - uploads the ZIP as a release asset, and
   - opens a PR patching `CHANGELOG.md`.
5. The new version appears on the Marketplace (updates are typically live within minutes,
   though they may be re-scanned).

### Option B — manual publish from your machine

```bash
export PUBLISH_TOKEN="your-marketplace-token"
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="your-passphrase"

./gradlew publishPlugin
```

`publishPlugin` builds, signs, and uploads in one step. Use this for a quick update without
going through a GitHub Release.

#### Release channels (optional)

By default the plugin publishes to the **stable** channel. To ship a pre-release to a
separate channel (users opt in via a custom repository URL), add a channel in
`build.gradle.kts`:

```kotlin
publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    channels = listOf("eap")   // e.g. for versions like 1.2.0-eap.1
}
```

---

## 5. Notes & troubleshooting

- **"The first version must be uploaded manually"** — expected; do Section 3 once.
- **Version already exists** — bump `version` in `gradle.properties`; Marketplace rejects a
  duplicate version.
- **Signing fails** — confirm the three signing env vars/secrets are set and that
  `PRIVATE_KEY` is the PKCS#8 `private.pem` (not `private_encrypted.pem`), with the matching
  passphrase.
- **`publishPlugin` 401/403** — the `PUBLISH_TOKEN` is wrong/expired, or the plugin listing
  doesn't exist yet (do the manual first upload).
- **Compatibility** — `verifyPlugin` must pass; the current range is `since-build 252` with
  no upper bound. Narrow the upper bound in `build.gradle.kts` if you start using unstable APIs.
- **Reference:** <https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html> and
  <https://plugins.jetbrains.com/docs/intellij/plugin-signing.html>.
