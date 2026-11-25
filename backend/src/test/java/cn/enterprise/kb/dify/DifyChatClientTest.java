package cn.enterprise.kb.dify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DifyChatClientTest {
    private final DifyChatClient client = new DifyChatClient(null, null, new ObjectMapper());

    @Test
    void joinsAnswerAndReadsCitationsFromMessageEnd() {
        var answer = client.readResponse("""
                event: ping

                data: {"event":"message","answer":"报销审核"}

                data: {"event":"message","answer":"需要三个工作日。"}

                data: {"event":"message_end","conversation_id":"conv-1","message_id":"msg-1","metadata":{"retriever_resources":[{"dataset_id":"dataset-1","document_id":"doc-2","segment_id":"seg-3","content":"报销制度📎"}],"usage":{"prompt_tokens":31,"completion_tokens":8,"total_tokens":39}}}
                """);
        assertThat(answer.text()).isEqualTo("报销审核需要三个工作日。");
        assertThat(answer.conversationId()).isEqualTo("conv-1");
        assertThat(answer.references()).containsExactly(new DifyChatClient.Reference("dataset-1", "doc-2", "seg-3", "报销制度📎"));
        assertThat(answer.usage()).isEqualTo(new DifyChatClient.Usage(31L, 8L, 39L));
    }

    @Test
    void unknownUsageStaysNullAndChatflowTotalDoesNotInventTheSplit() {
        String end = "data: {\"event\":\"message_end\",\"conversation_id\":\"c\",\"message_id\":\"m\",\"metadata\":{}}";
        assertThat(client.readResponse(end).usage()).isEqualTo(new DifyChatClient.Usage(null, null, null));
        String workflow = "data: {\"event\":\"workflow_finished\",\"data\":{\"status\":\"succeeded\",\"total_tokens\":56}}\n\n";
        assertThat(client.readResponse(workflow + end).usage()).isEqualTo(new DifyChatClient.Usage(null, null, 56L));
    }

    @Test
    void aPartialAnswerWithoutTheCompletionEventFails() {
        assertThatThrownBy(() -> client.readResponse("data: {\"event\":\"message\",\"answer\":\"不完整\"}"))
                .isInstanceOf(DifyFailure.class);
    }
}
