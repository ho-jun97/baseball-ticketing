package com.baseball.ticketing.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GameTest {

    private Game newGame() {
        Stadium stadium = Stadium.builder().name("잠실야구장").address("서울").build();
        return Game.builder()
                .stadium(stadium)
                .homeTeam("LG")
                .awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Test
    void builder_defaultsStatusToScheduled() {
        assertThat(newGame().getStatus()).isEqualTo(GameStatus.SCHEDULED);
    }

    @Test
    void cancel_setsStatusToCanceled() {
        Game game = newGame();

        game.cancel();

        assertThat(game.getStatus()).isEqualTo(GameStatus.CANCELED);
    }

    @Test
    void close_setsStatusToClosed() {
        Game game = newGame();

        game.close();

        assertThat(game.getStatus()).isEqualTo(GameStatus.CLOSED);
    }
}
