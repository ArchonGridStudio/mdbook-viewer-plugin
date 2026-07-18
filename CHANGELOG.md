<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# mdbook-viewer-plugin Changelog

## [Unreleased]

## [1.1.0] - 2026-07-18

### Added

- Bundled preprocessors: `mdbook-mermaid`, `mdbook-toc`, and `mdbook-katex` are bundled for all
  supported platforms and placed on the serve process's `PATH`, so books that opt in via
  `[preprocessor.mermaid]` / `[preprocessor.toc]` / `[preprocessor.katex]` render without any extra
  install.

### Changed

- Targets IntelliJ Platform build 262 (2026.2) and newer, where JCEF is provided by the bundled
  `com.intellij.modules.jcef` plugin. Fixes the plugin being rejected as incompatible: it previously
  compiled against 252 while declaring a 253 floor, and the JCEF module it depends on exists only
  from 262 on.

## [1.0.0]

### Added

- mdBook Viewer tool window that renders a running mdBook inside the IDE.
- View a book by pasting the URL of a local `mdbook serve` instance.
- Automatic detection of mdBook projects in the workspace.
- Toolbar for the mdBook Viewer tool window.
- JetBrains Remote Development (Gateway) support.
- Self-contained distribution: the mdBook renderer is bundled for Windows x64, macOS (Intel and
  Apple Silicon), and Linux x64, and repositories are fetched with an embedded pure-JVM git. The
  plugin no longer requires `mdbook` or `git` to be installed, and downloads nothing at runtime.

### Changed

- The clone-and-serve path no longer shells out to a system `git` or `mdbook`; it uses the bundled
  renderer and embedded git. Unsupported platforms now report a clear message instead of a
  "not found" error.

[Unreleased]: https://github.com/ArchonGridStudio/mdbook-viewer-plugin/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/ArchonGridStudio/mdbook-viewer-plugin/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ArchonGridStudio/mdbook-viewer-plugin/commits/v1.0.0
