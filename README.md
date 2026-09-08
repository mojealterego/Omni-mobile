# OMNI Mobile

High-end native Android control center for the OMNI evolutionary agent.

SASOS → MARS → CWM/MCTS → populations/islands → verification → DGM/Pareto → RSI → R3Mem/Titans → checkpoint/promotion.

The repository contains a native Android/Jetpack Compose control center and CI pipeline. The Android client uses a real WebSocket transport and does not locally simulate evolution.

## Build
`gradle :app:assembleDebug -PomniWsUrl=ws://10.0.2.2:8000/ws`

## Security
The OpenAI API key stays server-side and is never packaged into the app.
