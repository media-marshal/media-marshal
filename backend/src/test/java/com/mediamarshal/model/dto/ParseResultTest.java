package com.mediamarshal.model.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParseResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesSingleEpisodeNumber() throws Exception {
        ParseResult result = objectMapper.readValue("{\"episode\":16}", ParseResult.class);

        assertThat(result.getEpisode()).isEqualTo(16);
        assertThat(result.getEpisodeEnd()).isNull();
        assertThat(result.getEpisodes()).containsExactly(16);
    }

    @Test
    void deserializesContiguousEpisodeArrayAsRange() throws Exception {
        ParseResult result = objectMapper.readValue("{\"episode\":[16,17]}", ParseResult.class);

        assertThat(result.getEpisode()).isEqualTo(16);
        assertThat(result.getEpisodeEnd()).isEqualTo(17);
        assertThat(result.getEpisodes()).containsExactly(16, 17);
    }

    @Test
    void deserializesLongContiguousEpisodeArrayAsRange() throws Exception {
        ParseResult result = objectMapper.readValue(
                "{\"episode\":[21,22,23,24,25,26,27,28]}",
                ParseResult.class
        );

        assertThat(result.getEpisode()).isEqualTo(21);
        assertThat(result.getEpisodeEnd()).isEqualTo(28);
        assertThat(result.getEpisodes()).containsExactly(21, 22, 23, 24, 25, 26, 27, 28);
    }

    @Test
    void rejectsNonContiguousEpisodeArray() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"episode\":[16,18]}", ParseResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only contiguous episode ranges are supported");
    }
}
