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
