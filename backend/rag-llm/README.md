# :backend:rag-llm

Owner: **AI-2**. See the [module contracts](../../docs/contracts/MODULE_CONTRACTS.md).

## Document ingestion

`ingestDocument` extracts text from text-based PDF and UTF-8 TXT, then indexes overlapping chunks
in Pinecone under `institution_<slug>`. Limits are 20 MiB per file and 250,000 extracted characters.
Image-only PDFs return a failure because they have no extractable text.

The BE-1 server injects a `RagDocumentSource` and passes a Pinecone API key and the index hostname to
`DefaultRagLlmService`. Use a dense cosine index with 1,024 dimensions for Pinecone's
`multilingual-e5-large` model. Supply the key from server configuration.

Each vector ID is `rag-file-<id>#<zero-based chunk index>`. Its metadata holds the document ID,
original filename, chunk index and text. Repeating the same upload job overwrites those vectors.
Use a new upload ID for changed content. A provider failure can leave some batches indexed.
Retry the same upload ID to finish and use only `COMPLETED` uploads when wiring retrieval.

## Institution Q&A

Wire the service in the BE-1 server with a `RagDocumentSource`, a `CompletedRagDocuments`
adapter, and values from server configuration. For example:

```kotlin
val service: RagLlmService = DefaultRagLlmService(
    documentSource = uploadSource,
    completedDocuments = completedUploadCatalog,
    pineconeApiKey = requireNotNull(System.getenv("PINECONE_API_KEY")),
    pineconeIndexHost = requireNotNull(System.getenv("PINECONE_INDEX_HOST")),
    chatBaseUrl = requireNotNull(System.getenv("RAG_CHAT_BASE_URL")),
    chatApiKey = requireNotNull(System.getenv("RAG_CHAT_API_KEY")),
    chatModel = requireNotNull(System.getenv("RAG_CHAT_MODEL")),
)
```

The chat base URL is the HTTPS prefix before `/chat/completions`. Set a model supported by your
OpenAI-compatible provider. The model receives the question and up to five passages from the
selected institution. Configure the provider for the data those documents contain.

Use `askRag(question, sessionId, institutionSlug)` after BE-2 checks the caller's session and
its institution. The catalog must return only current completed uploads. An unselected
institution prompts the user to choose one. No eligible evidence or an unsupported citation
produces an Indonesian abstention with an empty source list. When the answer includes citations,
each source contains the upload ID, the exact quoted text, and its retrieval similarity score.
`Answer.confidence` is the highest cited similarity clipped to 0–1, a retrieval signal for the UI.

Invalid input raises `IllegalArgumentException`. Provider failures raise `IOException` for the
gateway to translate. Persist the question and returned sources alongside the answer for future
evaluation. This method answers the current question independently of previous turns.

Run the ingestion-to-answer HTTP integration tests with `./gradlew :backend:rag-llm:test`.
They use local test endpoints for Pinecone and chat completions.
