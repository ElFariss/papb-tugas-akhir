# Tunarasa (Android / Kotlin)

Kotlin migration of Tunarasa, an accessibility platform that helps deaf and hard-of-hearing people use public
services. Final project for PAPB.

| Module | Role | Status |
|---|---|---|
| [`app`](app) | FE | placeholder |
| [`ai-client`](ai-client) | AI-1: on-device SIBI and speech | implemented |
| [`backend/server`](backend/server), [`backend/platform-core`](backend/platform-core) | BE-1 | placeholder |
| [`backend/realtime-chat`](backend/realtime-chat) | BE-2 | placeholder |
| [`backend/rag-llm`](backend/rag-llm) | AI-2 | placeholder |
| [`contracts`](contracts) | shared types and the ownership map | draft |

## Start

Needs JDK 21 and Android SDK 36.

```bash
scripts/git/setup.sh
```

```bash
./gradlew build
```

The first build downloads the MediaPipe hand model (7.8 MB).

## Rules

- [Git contract](docs/contracts/GIT_CONTRACT.md): how to push and pull
- [Module contracts](docs/contracts/MODULE_CONTRACTS.md): who owns what, and the interfaces between modules
