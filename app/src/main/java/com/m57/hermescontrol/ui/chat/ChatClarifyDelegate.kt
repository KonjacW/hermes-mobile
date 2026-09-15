package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsMethods
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatClarifyDelegate(
    private val uiState: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val persistMessage: suspend (ChatMessage, String) -> Unit,
    private val wsClient: HermesWsClient = HermesWsClient,
    private val trackRequest: (String, String) -> Unit = { _, _ -> },
) {
    fun respondToClarify(option: String) {
        val clarify = uiState.value.clarifyRequest
        // Only use synthesized qid when this is a true batch or legacy with explicit qid.
        val qid =
            if (clarify != null &&
                (clarify.questionId != null || clarify.questions.isNotEmpty())
            ) {
                clarify.resolvedQuestions.firstOrNull()?.qid ?: clarify.questionId
            } else {
                null
            }
        if (qid != null && clarify?.resolvedQuestions?.size == 1) {
            respondToClarifyBatch(mapOf(qid to option))
        } else {
            respondToClarifyBatch(emptyMap(), singleFallbackAnswer = option)
        }
    }

    fun respondToClarifyBatch(
        answers: Map<String, String>,
        singleFallbackAnswer: String? = null,
    ) {
        val sessionId = uiState.value.currentSessionId ?: return
        val clarify = uiState.value.clarifyRequest
        val clarifyId = clarify?.clarifyId
        val isBatch = !clarify?.questions.isNullOrEmpty()
        val questions = clarify?.resolvedQuestions.orEmpty()
        uiState.update { it.copy(clarifyRequest = null) }

        val displayContent =
            if (questions.size > 1) {
                questions
                    .mapIndexed { index, q ->
                        val ans = answers[q.qid]?.trim().orEmpty()
                        "${index + 1}. ${ans.ifEmpty { "(Skipped)" }}"
                    }.joinToString("\n")
            } else {
                val loneAns = answers.values.firstOrNull()?.trim() ?: singleFallbackAnswer?.trim().orEmpty()
                loneAns
            }

        val userMessage =
            ChatMessage(
                role = MessageRole.USER,
                content = displayContent,
            )

        uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isAgentTyping = true,
            )
        }

        scope.launch(ioDispatcher) {
            persistMessage(userMessage, sessionId)
        }

        scope.launch(ioDispatcher) {
            if (isBatch) {
                for (q in questions) {
                    val ans = answers[q.qid]?.trim().orEmpty()
                    val params =
                        mutableMapOf<String, Any>(
                            "session_id" to sessionId,
                            "response" to ans,
                            "answer" to ans,
                            "question_id" to q.qid,
                        )
                    if (clarifyId != null) {
                        params["clarify_id"] = clarifyId
                        params["request_id"] = clarifyId
                    }
                    wsClient.send(
                        method = WsMethods.CLARIFY_RESPOND,
                        params = params,
                        onSent = { id -> trackRequest(id, WsMethods.CLARIFY_RESPOND) },
                    )
                }
            } else {
                // Legacy single: only include question_id when explicitly present.
                val qid = clarify?.questionId
                val ans = answers.values.firstOrNull() ?: singleFallbackAnswer.orEmpty()
                val params =
                    mutableMapOf<String, Any>(
                        "session_id" to sessionId,
                        "response" to ans,
                        "answer" to ans,
                    )
                if (clarifyId != null) {
                    params["clarify_id"] = clarifyId
                    params["request_id"] = clarifyId
                }
                if (qid != null) {
                    params["question_id"] = qid
                }
                wsClient.send(
                    method = WsMethods.CLARIFY_RESPOND,
                    params = params,
                    onSent = { id -> trackRequest(id, WsMethods.CLARIFY_RESPOND) },
                )
            }
        }
    }

    /**
     * Surface a pending clarify carried by session.resume / session.info
     * (server `_session_info_payload.pending_clarify`) — the reconnect path
     * that replays a prompt whose original `clarify.request` event was already
     * consumed and is never re-emitted. Mirrors ChatApprovalsDelegate's
     * pending-approval replay.
     */
    fun maybeSurfacePendingClarify(map: Map<*, *>) {
        val clarify = parseClarifyUi(map) ?: return
        val current = uiState.value.clarifyRequest
        val alreadyShown = clarify.clarifyId != null && current?.clarifyId == clarify.clarifyId
        if (!alreadyShown) {
            uiState.update { it.copy(clarifyRequest = clarify) }
        }
    }
}

/**
 * Convert a backend `pending_clarify` map (snake_case, from session.resume /
 * session.info) into the [ClarifyUi] the chat screen renders. Mirrors the
 * `clarify.request` parsing in [EventParser] so the reconnect and live paths
 * agree.
 */
internal fun parseClarifyUi(map: Map<*, *>): ClarifyUi? {
    val clarifyId = map["request_id"] as? String ?: map["clarify_id"] as? String

    @Suppress("UNCHECKED_CAST")
    val rawQuestions = map["questions"] as? List<*>
    val questions =
        if (rawQuestions != null && rawQuestions.isNotEmpty()) {
            rawQuestions.mapIndexedNotNull { index, item ->
                val q = item as? Map<*, *> ?: return@mapIndexedNotNull null
                val qText = q["question"] as? String ?: return@mapIndexedNotNull null
                val qid = q["qid"] as? String ?: "q$index"
                @Suppress("UNCHECKED_CAST")
                val qChoices = (q["choices"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val qMulti = q["multi_select"] as? Boolean ?: false
                ClarifyQuestionUi(
                    qid = qid,
                    question = qText,
                    choices = qChoices,
                    multiSelect = qMulti,
                )
            }
        } else {
            emptyList()
        }

    val text = map["question"] as? String ?: map["text"] as? String
    @Suppress("UNCHECKED_CAST")
    val options = (map["choices"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val questionId = map["qid"] as? String ?: map["question_id"] as? String
    val multiSelect = map["multi_select"] as? Boolean ?: false

    val hasBatch = questions.isNotEmpty()
    val hasSingle = !text.isNullOrBlank() || options.isNotEmpty()
    if (!hasBatch && !hasSingle) return null

    return ClarifyUi(
        text = text.orEmpty(),
        options = options,
        clarifyId = clarifyId,
        questionId = questionId,
        multiSelect = multiSelect,
        questions = questions,
    )
}
