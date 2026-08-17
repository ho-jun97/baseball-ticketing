package com.baseball.ticketing.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * GameSeat는 CLAUDE.md 핵심 과제(동일 좌석 동시 예매 안전 처리)의 중심 엔티티다.
 * 여기서는 순수 상태 전이 로직만 검증하고, @Version 낙관적 락이 실제 동시 쓰기를 막는지는
 * {@link GameSeatOptimisticLockTest}(Testcontainers 기반)에서 검증한다.
 */
class GameSeatTest {

    private GameSeat newAvailableGameSeat() {
        Stadium stadium = Stadium.builder().name("잠실야구장").address("서울").build();
        Section section = Section.builder().stadium(stadium).name("1루 응원석").price(15000).totalSeats(100).build();
        Seat seat = Seat.builder().section(section).seatRow("A").seatNumber("1").build();
        Game game = Game.builder()
                .stadium(stadium)
                .homeTeam("LG")
                .awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1))
                .build();
        return GameSeat.builder().game(game).seat(seat).build();
    }

    @Test
    void builder_defaultsStatusToAvailable() {
        assertThat(newAvailableGameSeat().getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
    }

    @Test
    void hold_fromAvailable_movesToHoldWithExpiry() {
        GameSeat gameSeat = newAvailableGameSeat();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(5);

        gameSeat.hold(expireAt);

        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.HOLD);
        assertThat(gameSeat.getHoldExpireAt()).isEqualTo(expireAt);
    }

    @Test
    void hold_whenAlreadyHold_throws() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> gameSeat.hold(LocalDateTime.now().plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hold_whenAlreadySold_throws() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));
        gameSeat.sell();

        assertThatThrownBy(() -> gameSeat.hold(LocalDateTime.now().plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sell_fromHold_movesToSoldAndClearsExpiry() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));

        gameSeat.sell();

        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.SOLD);
        assertThat(gameSeat.getHoldExpireAt()).isNull();
    }

    @Test
    void sell_whenNotHold_throws() {
        GameSeat gameSeat = newAvailableGameSeat();

        assertThatThrownBy(gameSeat::sell).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void release_fromHold_resetsToAvailable() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));

        gameSeat.release();

        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(gameSeat.getHoldExpireAt()).isNull();
    }

    @Test
    void release_fromSold_resetsToAvailable() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));
        gameSeat.sell();

        gameSeat.release();

        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
    }

    @Test
    void isHoldExpired_true_whenHoldAndPastExpiry() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().minusSeconds(1));

        assertThat(gameSeat.isHoldExpired(LocalDateTime.now())).isTrue();
    }

    @Test
    void isHoldExpired_false_whenHoldButNotYetExpired() {
        GameSeat gameSeat = newAvailableGameSeat();
        gameSeat.hold(LocalDateTime.now().plusMinutes(5));

        assertThat(gameSeat.isHoldExpired(LocalDateTime.now())).isFalse();
    }

    @Test
    void isHoldExpired_false_whenAvailable() {
        assertThat(newAvailableGameSeat().isHoldExpired(LocalDateTime.now())).isFalse();
    }
}
