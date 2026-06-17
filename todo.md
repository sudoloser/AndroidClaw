We've got several connected issues and feature gaps to address:

1. web_fetch returns empty/blank results
The web_fetch tool is available but when invoked, it returns nothing useful — empty payloads for real URLs. This kills the ability to browse documentation, fetch API responses, or do any web research.

2. Runtime tool restrictions are misconfigured
The system prompt lists tools like shell, file_write, http_request, cron_add, etc. as available with "full" autonomy, but at runtime they're excluded by policy. Only a subset actually works (file_read, glob_search, content_search, memory_recall, web_fetch, web_search_tool, and a few others). This is inconsistent and blocks fixing issue #1.

3. No in-app TOML config editor
There's no way to directly view and edit the bot's configuration TOML file from within the app itself. This means every config change requires going outside the app, making iteration slow. Need a UI screen (or at least a /config command) that reads the TOML, lets you edit it inline, validates it on save, and writes it back. Bonus: schema-aware editing with descriptions for each field.

4. No built-in file manager
The app's working directory and project files are invisible from inside the app. Need a full file explorer accessible from the bot — list directories, navigate, view files, upload new files (from the device/Telegram), delete/rename, and preview common types (text, images, PDFs). A /files command with a navigable tree view would go a long way. Download (export) files too.

What needs to happen:
- Check how web_fetch is implemented — it's probably using a provider (fast_html2md, nanohtml2text, firecrawl, tavily) that's either not compiled in or failing silently.
- The Cargo features for web_fetch are: web-fetch-html2md = ["dep:fast_html2md"] and web-fetch-plaintext = ["dep:nanohtml2text"]. Make sure at least one of these is in the default features and compiled correctly.
- Alternatively, if we can get shell unblocked, we can use curl directly as a fallback for web fetching.
- The runtime policy file needs to be updated so the tools listed in the system prompt actually match what's allowed at execution time.
- Add a TOML config editor — either command-based (/config get/set) or a full interactive editor that saves back to disk.
- Build a file manager module: list directory contents, read files, upload via Telegram attachment, delete/move, and preview.

Context: This is on Android (Termux), Rust-based binary ~3MB. The AndroidClaw project (github.com/AnswerZhao/AndroidClaw) was referenced as a possible reference for how to handle networking on this platform.
