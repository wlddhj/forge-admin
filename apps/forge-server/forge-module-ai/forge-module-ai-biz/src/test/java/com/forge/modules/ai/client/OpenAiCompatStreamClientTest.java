package com.forge.modules.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原生 SSE 行解析单测：reasoning/content 增量分离、usage 附着 DONE 帧、容错行为
 *
 * @author standadmin
 */
class OpenAiCompatStreamClientTest {

    private final OpenAiCompatStreamClient client = new OpenAiCompatStreamClient(new ObjectMapper());

    private List<StreamFrame> read(String sse) {
        List<StreamFrame> frames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(sse))) {
            client.readFrames(reader, frames::add, Long.MAX_VALUE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return frames;
    }

    @Test
    @DisplayName("reasoning/content 增量分离透传，usage 附着 DONE 帧")
    void 解析reasoning与content与usage() {
        String sse = """
                data: {"choices":[{"delta":{"reasoning_content":"思考A"}}]}

                data: {"choices":[{"delta":{"content":"正文B"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3}}

                data: [DONE]
                """;
        assertThat(read(sse)).containsExactly(
                StreamFrame.reasoning("思考A"),
                StreamFrame.content("正文B"),
                StreamFrame.done(7L, 3L));
    }

    @Test
    @DisplayName("上游无 [DONE] 以 EOF 收口（usage 仍保留）")
    void 无DONE以EOF收口() {
        String sse = """
                data: {"choices":[{"delta":{"content":"hi"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        assertThat(read(sse)).containsExactly(
                StreamFrame.content("hi"),
                StreamFrame.done(1L, 1L));
    }

    @Test
    @DisplayName("空增量不发帧；非思考模型无 reasoning 则只有 content")
    void 空增量与无reasoning不发帧() {
        String sse = """
                data: {"choices":[{"delta":{"content":""}}]}

                data: {"choices":[{"delta":{"content":"ok"}}]}

                data: [DONE]
                """;
        assertThat(read(sse)).containsExactly(
                StreamFrame.content("ok"),
                StreamFrame.done(null, null));
    }

    @Test
    @DisplayName("非 data 行与坏 JSON 行跳过，usage-only 块只累积 usage")
    void 容错行为() {
        String sse = """
                event: ping

                data: not-json

                data: {"choices":[{"delta":{"content":"a"}}]}

                data: {"usage":{"prompt_tokens":2}}

                data: [DONE]
                """;
        assertThat(read(sse)).containsExactly(
                StreamFrame.content("a"),
                StreamFrame.done(2L, null));
    }

    @Test
    @DisplayName("空 data: 行不终止流（保持读取直到 [DONE]）")
    void 空data行不终止流() {
        String sse = """
                data:

                data: {"choices":[{"delta":{"content":"a"}}]}

                data: [DONE]
                """;
        assertThat(read(sse)).containsExactly(
                StreamFrame.content("a"),
                StreamFrame.done(null, null));
    }
}
