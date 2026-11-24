package id.tunarasa.backend.chat.gateway

/**
 * PLACEHOLDER. Owner: BE-2.
 *
 * Forwards chat, gesture, FAQ and evaluation calls to `RagLlmService`.
 *
 * Migrate from:
 * - `backend/app/api/v1/endpoints/gesture.py`
 * - `backend/app/api/v1/endpoints/rag.py`
 * - `backend/app/api/v1/endpoints/faq_recommendation.py`
 *
 * Exposes:
 * - `POST /api/v1/gesture/ask`: body is AI-1 `GestureAskRequest`
 * - FAQ and RAG proxy endpoints
 */
internal object AiGatewayRoutes
