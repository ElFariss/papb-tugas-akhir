# Tunarasa (Android / Kotlin)

Kotlin migration of Tunarasa, an accessibility platform that helps deaf and hard-of-hearing people use public
services. Final project for PAPB.

| Module | Role | Status |
|---|---|---|
| [`app`](app) | FE | Android public and admin flows |
| [`ai-client`](ai-client) | AI-1: on-device SIBI and speech | implemented |
| [`backend/server`](backend/server), [`backend/platform-core`](backend/platform-core) | BE-1 | Ktor, PostgreSQL, Redis, and JWT auth |
| [`backend/realtime-chat`](backend/realtime-chat) | BE-2 | conversations, validation queue, QA logs, and notes |
| [`backend/rag-llm`](backend/rag-llm) | AI-2 | ingestion and cited answers |
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

The debug app uses `http://10.0.2.2:8080` to reach a server on the emulator host. Set
`tunarasa.apiBaseUrl`, `tunarasa.supabaseUrl`, and `tunarasa.supabasePublicKey` as Gradle properties
to point the app and admin sign-in at your deployment. Use the Supabase publishable or anon key in the
APK. Server setup is documented in the [BE-1 guide](docs/BE1_IMPLEMENTATION_GUIDE.md).

## Rules

- [Git contract](docs/contracts/GIT_CONTRACT.md): how to push and pull
- [Module contracts](docs/contracts/MODULE_CONTRACTS.md): who owns what, and the interfaces between modules
