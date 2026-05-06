# Hawker Rush: Bug Fixes

This document tracks identified and resolved bugs in the Hawker Rush codebase.

| ID | Date | Description | Status |
| :--- | :--- | :--- | :--- |
| FIX-001 | 2025-05-15 | Updated `calculateStatBoost` in `MainViewModel.kt` to accept a hex map instead of `GameState`. This ensures that Bak Kut Teh stalls re-enabled during wave completion correctly provide bonuses to adjacent ATMs in the same wave. | Resolved |
