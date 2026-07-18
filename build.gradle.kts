import org.gradle.api.file.ArchiveOperations
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// The 2026.2 (262) platform ships Java 25 bytecode and Kotlin 2.4 metadata, so it must be compiled
// against with a JDK 25 toolchain and the Kotlin 2.4 compiler (pinned in settings.gradle.kts). The
// IntelliJ Platform plugin fixes the Java target at 25 for a 262 build, so Kotlin emits the same
// target — keeping Gradle's Java/Kotlin JVM-target consistency check satisfied.
kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

val jgitVersion = providers.gradleProperty("jgitVersion")
val mdbookVersion = providers.gradleProperty("mdbookVersion")

dependencies {
    testImplementation("junit:junit:4.13.2")

    // Embedded, pure-JVM git so no native `git` binary is required (FR-002). ssh.apache adds
    // scp-style SSH support via Apache MINA sshd (also pure Java). Bundled into the plugin.
    implementation("org.eclipse.jgit:org.eclipse.jgit:${jgitVersion.get()}")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:${jgitVersion.get()}")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Build against the sinceBuild floor (262). The plugin depends on the
        // com.intellij.modules.jcef module, which is only present from build 262 onward
        // (it does not exist in 252 or 253), so an earlier target makes the IDE reject the
        // plugin at load time with "dependency on com.intellij.modules.jcef which is not installed".
        intellijIdea("2026.2")
        // JCEF is a bundled plugin (id com.intellij.modules.jcef) from build 262 on, not core
        // platform, so com.intellij.ui.jcef.* is only on the compile classpath when this is added.
        // Mirrors the <depends> in plugin.xml, which grants the same access at runtime.
        bundledPlugin("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // sinceBuild is 262: the plugin depends on the com.intellij.modules.jcef module,
            // which is only available from build 262 onward. The build target above matches this
            // floor. Upper bound left open (verified compatible on 2026.2 / 262).
            sinceBuild = "262"
            untilBuild = provider { null }
        }

        // Change-notes come from CHANGELOG.md: the section for the current version once it exists
        // (after a release promotes it), otherwise the [Unreleased] section — so both a dev build and
        // a released build embed meaningful notes even though upcoming changes live under [Unreleased].
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(providers.gradleProperty("version").get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Signing + publishing read their secrets from environment variables so nothing
    // sensitive lives in the repo. Supply them locally or as CI secrets when releasing.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// ---------------------------------------------------------------------------------------------
// Self-contained renderer: download the pinned mdBook binaries, verify their SHA-256, and bundle
// them (gzip-compressed) so the plugin needs no external install (FR-001, FR-003, FR-005).
// ---------------------------------------------------------------------------------------------

// releaseAssetSuffix + entry-inside-archive + gradle property holding the SHA-256, keyed by resource dir.
data class MdbookTarget(val dir: String, val assetSuffix: String, val entry: String, val shaProperty: String)

val MDBOOK_TARGETS = listOf(
    MdbookTarget("linux-x64", "x86_64-unknown-linux-gnu.tar.gz", "mdbook", "mdbookSha256LinuxX64"),
    MdbookTarget("macos-x64", "x86_64-apple-darwin.tar.gz", "mdbook", "mdbookSha256MacosX64"),
    MdbookTarget("macos-arm64", "aarch64-apple-darwin.tar.gz", "mdbook", "mdbookSha256MacosArm64"),
    MdbookTarget("windows-x64", "x86_64-pc-windows-msvc.zip", "mdbook.exe", "mdbookSha256WindowsX64"),
)

/**
 * Self-contained (no reference to script-level declarations, so it is configuration-cache safe):
 * each spec is "dir|assetSuffix|entry|sha256". Downloads the pinned release asset, verifies its
 * SHA-256, extracts the single binary, and stores it gzip-compressed at `<dir>/mdbook.gz`.
 */
abstract class BundleMdbookTask : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val targetSpecs: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Internal abstract val downloadDir: DirectoryProperty
    @get:Inject abstract val archiveOps: ArchiveOperations

    @TaskAction
    fun bundle() {
        val v = version.get()
        val out = outputDir.get().asFile
        val dl = downloadDir.get().asFile.also { it.mkdirs() }

        targetSpecs.get().forEach { spec ->
            val (dir, assetSuffix, entry, sha) = spec.split("|")
            val assetName = "mdbook-v$v-$assetSuffix"
            val archive = File(dl, assetName)
            if (!archive.exists()) {
                logger.lifecycle("Downloading $assetName")
                URI("https://github.com/rust-lang/mdBook/releases/download/v$v/$assetName").toURL()
                    .openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual.equals(sha, ignoreCase = true)) {
                "SHA-256 mismatch for $assetName:\n  expected $sha\n  actual   $actual"
            }
            val tree = if (assetName.endsWith(".zip")) archiveOps.zipTree(archive)
            else archiveOps.tarTree(archiveOps.gzip(archive))
            val binary = tree.matching { include(entry) }.singleFile

            val target = File(out, "$dir/mdbook.gz").also { it.parentFile.mkdirs() }
            GZIPOutputStream(target.outputStream()).use { gz -> binary.inputStream().use { it.copyTo(gz) } }
        }
        File(out, "version.txt").writeText(v)
    }
}

val bundleMdbook = tasks.register<BundleMdbookTask>("bundleMdbook") {
    group = "build"
    description = "Downloads, checksum-verifies, and bundles the pinned mdBook binaries."
    version.set(mdbookVersion)
    targetSpecs.set(MDBOOK_TARGETS.map { t ->
        "${t.dir}|${t.assetSuffix}|${t.entry}|${providers.gradleProperty(t.shaProperty).get()}"
    })
    outputDir.set(layout.projectDirectory.dir("src/main/resources/mdbook"))
    downloadDir.set(layout.buildDirectory.dir("mdbook-download"))
}

// The binaries must be on the classpath before resources are processed into the jar.
tasks.named("processResources") { dependsOn(bundleMdbook) }

// ---------------------------------------------------------------------------------------------
// Bundled preprocessors: the same self-contained approach as the renderer, for the preprocessor
// binaries mdBook shells out to (mermaid, toc, katex). At runtime they are extracted into one
// directory that is prepended to the `mdbook serve` process PATH, so mdBook finds them by name.
// Adding another self-contained preprocessor is a single PREPROC_TOOLS entry + its gradle.properties
// tag/infix/checksums — no new plumbing.
// ---------------------------------------------------------------------------------------------

// Shared platform axis: resource dir + release-asset suffix + windows flag + sha-property key part.
data class PreprocPlatform(val dir: String, val assetSuffix: String, val windows: Boolean, val shaKey: String)

val PREPROC_PLATFORMS = listOf(
    PreprocPlatform("linux-x64", "x86_64-unknown-linux-gnu.tar.gz", false, "LinuxX64"),
    PreprocPlatform("macos-x64", "x86_64-apple-darwin.tar.gz", false, "MacosX64"),
    PreprocPlatform("macos-arm64", "aarch64-apple-darwin.tar.gz", false, "MacosArm64"),
    PreprocPlatform("windows-x64", "x86_64-pc-windows-msvc.zip", true, "WindowsX64"),
)

// Tool axis: name (== binary name, sans .exe) + GitHub repo + gradle-property prefix (Tag/AssetInfix/Sha256*).
data class PreprocTool(val name: String, val repo: String, val propPrefix: String)

val PREPROC_TOOLS = listOf(
    PreprocTool("mdbook-mermaid", "badboy/mdbook-mermaid", "mdbookMermaid"),
    PreprocTool("mdbook-toc", "badboy/mdbook-toc", "mdbookToc"),
    PreprocTool("mdbook-katex", "lzanini/mdbook-katex", "mdbookKatex"),
)

/**
 * Configuration-cache safe: each spec is "outRelPath|downloadUrl|entry|sha256". Downloads the
 * release asset, verifies its SHA-256, extracts the single binary, and stores it gzip-compressed at
 * outRelPath. manifestLines ("<tool> <tag>") are written to manifest.txt so the runtime can
 * version-key its extracted copies.
 */
abstract class BundlePreprocessorsTask : DefaultTask() {
    @get:Input abstract val specs: ListProperty<String>
    @get:Input abstract val manifestLines: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Internal abstract val downloadDir: DirectoryProperty
    @get:Inject abstract val archiveOps: ArchiveOperations

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile
        val dl = downloadDir.get().asFile.also { it.mkdirs() }

        specs.get().forEach { spec ->
            val (outRel, url, entry, sha) = spec.split("|")
            val assetName = url.substringAfterLast('/')
            val archive = File(dl, assetName)
            if (!archive.exists()) {
                logger.lifecycle("Downloading $assetName")
                URI(url).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual.equals(sha, ignoreCase = true)) {
                "SHA-256 mismatch for $assetName:\n  expected $sha\n  actual   $actual"
            }
            val tree = if (assetName.endsWith(".zip")) archiveOps.zipTree(archive)
            else archiveOps.tarTree(archiveOps.gzip(archive))
            val binary = tree.matching { include(entry) }.singleFile

            val target = File(out, outRel).also { it.parentFile.mkdirs() }
            GZIPOutputStream(target.outputStream()).use { gz -> binary.inputStream().use { it.copyTo(gz) } }
        }
        File(out, "manifest.txt").writeText(manifestLines.get().joinToString("\n"))
    }
}

// Every (tool, platform) output path, relative to src/main/resources/mdbook-tools. Reused by the task
// and the distribution gate.
val preprocOutputPaths = PREPROC_TOOLS.flatMap { tool ->
    PREPROC_PLATFORMS.map { p -> "${tool.name}/${p.dir}/${tool.name}.gz" }
}

val bundlePreprocessors = tasks.register<BundlePreprocessorsTask>("bundlePreprocessors") {
    group = "build"
    description = "Downloads, checksum-verifies, and bundles the pinned mdBook preprocessor binaries."
    specs.set(PREPROC_TOOLS.flatMap { tool ->
        val tag = providers.gradleProperty("${tool.propPrefix}Tag").get()
        val infix = providers.gradleProperty("${tool.propPrefix}AssetInfix").get()
        PREPROC_PLATFORMS.map { p ->
            val url = "https://github.com/${tool.repo}/releases/download/$tag/$infix${p.assetSuffix}"
            val entry = if (p.windows) "${tool.name}.exe" else tool.name
            val sha = providers.gradleProperty("${tool.propPrefix}Sha256${p.shaKey}").get()
            "${tool.name}/${p.dir}/${tool.name}.gz|$url|$entry|$sha"
        }
    })
    manifestLines.set(PREPROC_TOOLS.map { tool ->
        "${tool.name} ${providers.gradleProperty("${tool.propPrefix}Tag").get()}"
    })
    outputDir.set(layout.projectDirectory.dir("src/main/resources/mdbook-tools"))
    downloadDir.set(layout.buildDirectory.dir("preprocessor-download"))
}

tasks.named("processResources") { dependsOn(bundlePreprocessors) }

// Distribution gate: bundled binaries present + artifact under the size ceiling (SC-005, FR-003).
val expectedBundledFiles = MDBOOK_TARGETS.map { "${it.dir}/mdbook.gz" } + "version.txt"
val verifyPluginDistribution = tasks.register("verifyPluginDistribution") {
    group = "verification"
    description = "Asserts the bundled binaries are present and the plugin artifact is within the size ceiling."
    dependsOn("buildPlugin")
    val resDir = layout.projectDirectory.dir("src/main/resources/mdbook").asFile
    val toolsResDir = layout.projectDirectory.dir("src/main/resources/mdbook-tools").asFile
    val distDir = layout.buildDirectory.dir("distributions").get().asFile
    val expected = expectedBundledFiles
    val expectedPreproc = preprocOutputPaths + "manifest.txt"
    // Renderer (~19 MB) + three preprocessors across four platforms push the artifact past the
    // original 40 MB ceiling; 64 MB keeps a real guard against accidental bloat.
    val ceilingBytes = 64L * 1024 * 1024
    doLast {
        val missing = expected.filterNot { File(resDir, it).let { f -> f.exists() && f.length() > 0 } } +
            expectedPreproc.filterNot { File(toolsResDir, it).let { f -> f.exists() && f.length() > 0 } }
        check(missing.isEmpty()) { "Bundled binaries missing/empty: $missing" }

        val zip = distDir.listFiles { f: File -> f.name.endsWith(".zip") }?.maxByOrNull { it.lastModified() }
            ?: error("No plugin distribution zip found under $distDir")
        check(zip.length() <= ceilingBytes) {
            "Plugin artifact ${zip.name} is ${zip.length() / 1024 / 1024} MB, over the ${ceilingBytes / 1024 / 1024} MB ceiling (SC-005)"
        }
        logger.lifecycle("verifyPluginDistribution OK: ${zip.name} = ${zip.length() / 1024 / 1024} MB")
    }
}

tasks.named("check") { dependsOn(verifyPluginDistribution) }

// Helper for version bumps: prints fresh SHA-256s to paste into gradle.properties.
val checksumSpecs = MDBOOK_TARGETS.map { "${it.shaProperty}|${it.assetSuffix}" }
tasks.register("printMdbookChecksums") {
    group = "help"
    val v = mdbookVersion
    val dl = layout.buildDirectory.dir("mdbook-download")
    val specs = checksumSpecs
    doLast {
        val version = v.get()
        val dir = dl.get().asFile.also { it.mkdirs() }
        specs.forEach { spec ->
            val (prop, assetSuffix) = spec.split("|")
            val assetName = "mdbook-v$version-$assetSuffix"
            val archive = File(dir, assetName)
            if (!archive.exists()) URI("https://github.com/rust-lang/mdBook/releases/download/v$version/$assetName")
                .toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            val sha = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
            println("$prop = $sha")
        }
    }
}

// Helper for preprocessor version bumps: prints fresh SHA-256s to paste into gradle.properties.
val preprocChecksumSpecs = PREPROC_TOOLS.flatMap { tool ->
    val tag = providers.gradleProperty("${tool.propPrefix}Tag").get()
    val infix = providers.gradleProperty("${tool.propPrefix}AssetInfix").get()
    PREPROC_PLATFORMS.map { p ->
        val url = "https://github.com/${tool.repo}/releases/download/$tag/$infix${p.assetSuffix}"
        "${tool.propPrefix}Sha256${p.shaKey}|$url"
    }
}
tasks.register("printPreprocessorChecksums") {
    group = "help"
    val dl = layout.buildDirectory.dir("preprocessor-download")
    val specs = preprocChecksumSpecs
    doLast {
        val dir = dl.get().asFile.also { it.mkdirs() }
        specs.forEach { spec ->
            val (prop, url) = spec.split("|")
            val archive = File(dir, url.substringAfterLast('/'))
            if (!archive.exists()) URI(url).toURL()
                .openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            val sha = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
            println("$prop = $sha")
        }
    }
}
