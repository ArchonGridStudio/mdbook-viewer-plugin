# mdBook Viewer

![Build](https://github.com/ArchonGridStudio/mdbook-viewer-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/32609.svg)](https://plugins.jetbrains.com/plugin/32609)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32609.svg)](https://plugins.jetbrains.com/plugin/32609)

mdBook Viewer renders an [mdBook](https://rust-lang.github.io/mdBook/) inside a JetBrains IDE tool
window, so you can read and navigate a book without leaving your editor or standing up a local
server.

Point it at a GitHub repository (public or private) or any URL that hosts an mdBook; the plugin
fetches the source and renders the book internally, in the background. No `mdbook serve` and no
local mdBook installation required.

## Features

- A dedicated **mdBook Viewer** tool window that displays the book in an embedded browser.
- Point it at a GitHub repository (public or private), or any URL that hosts an mdBook.
- Fetches the source and renders the book internally -- no `mdbook serve`, no local mdBook install.
- Fully supported in JetBrains Remote Development / Gateway.

## Getting started

1. Open the **mdBook Viewer** tool window (right-hand tool strip, or **View | Tool Windows | mdBook Viewer**).
2. Enter the URL of a GitHub repository (public or private), or another source that hosts an mdBook.
3. The plugin fetches the source and renders the book in the background, then displays it in the tool window.

## Requirements

- An IntelliJ-based IDE, build 2025.3 (253) or newer.

## Installation

- **JetBrains Marketplace:** <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd>, search for **mdBook Viewer**, and click <kbd>Install</kbd>.
- **From disk:** download the [latest release](https://github.com/ArchonGridStudio/mdbook-viewer-plugin/releases/latest) and install via <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>gear icon</kbd> > <kbd>Install plugin from disk...</kbd>.

## License

Licensed under the [MIT License](./LICENSE).

---
Built with the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
