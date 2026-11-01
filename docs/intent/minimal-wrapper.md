# Intent: Minimal llama.cpp Android wrapper

Confirmed by the user.

- **Outcome:** A minimal Android APK wrapper for upstream llama.cpp (`llama-server`) and its built-in WebUI.
- **User:** The project owner, using a Samsung Galaxy S25.
- **Why:** Existing FOSS Android local-chat apps feel bloated and buggy.
- **Success:** Install the APK, import a GGUF, start the server, chat, and reliably stop/restart. Verify this workflow on the user's physical Galaxy S25.
- **Constraint:** CPU-only first. No Termux, root, or computer required for everyday use. Prioritize reliable basics over extra features or interface polish.
- **Out of scope:** A custom chat UI, extra features, GPU/NPU acceleration, and broad device support for this initial version.

## Verification boundary

This records the desired outcome, not evidence that the current scaffold works. Compilation and physical-device validation must be demonstrated separately.
