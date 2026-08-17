package com.baseball.ticketing.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseball.ticketing.support.AbstractPersistenceTest;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CLAUDE.md 핵심 과제 검증: 동일 GameSeat row에 대한 동시 갱신 중 하나만 성공해야 한다.
 * "다른 트랜잭션이 먼저 커밋해 version을 올린" 상황을 DB에 직접 반영해 시뮬레이션하고, 이후
 * (이미 stale해진) 낙관적 락(@Version)을 가진 채로 flush 하면 OptimisticLockException이
 * 발생하는지 확인한다 — 서비스 레이어는 이 예외를 409 SEAT_ALREADY_TAKEN으로 변환하게 된다.
 */
class GameSeatOptimisticLockTest extends AbstractPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentHold_secondWriterFailsWithOptimisticLock() {
        GameSeat gameSeat = persistAvailableGameSeat();
        Long id = gameSeat.getId();
        em.getEntityManager().clear();

        GameSeat loaded = em.find(GameSeat.class, id);

        // 다른 트랜잭션이 먼저 이 좌석을 HOLD로 바꾸고 커밋한 상황을 시뮬레이션 (version 직접 증가)
        jdbcTemplate.update(
                "UPDATE game_seat SET status = 'HOLD', version = version + 1 WHERE id = ?", id);

        loaded.hold(LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> em.getEntityManager().flush())
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void sequentialHold_succeedsAndIncrementsVersion() {
        GameSeat gameSeat = persistAvailableGameSeat();
        int versionBeforeHold = gameSeat.getVersion();

        gameSeat.hold(LocalDateTime.now().plusMinutes(5));
        em.getEntityManager().flush();
        em.getEntityManager().clear();

        GameSeat reloaded = em.find(GameSeat.class, gameSeat.getId());
        assertThat(reloaded.getStatus()).isEqualTo(GameSeatStatus.HOLD);
        assertThat(reloaded.getVersion()).isGreaterThan(versionBeforeHold);
    }

    private GameSeat persistAvailableGameSeat() {
        Stadium stadium = em.persistAndFlush(Stadium.builder().name("잠실야구장").address("서울").build());
        Game game = em.persistAndFlush(Game.builder()
                .stadium(stadium)
                .homeTeam("LG")
                .awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1))
                .build());
        Section section = em.persistAndFlush(Section.builder()
                .stadium(stadium)
                .name("1루 응원석")
                .price(15000)
                .totalSeats(100)
                .build());
        Seat seat = em.persistAndFlush(
                Seat.builder().section(section).seatRow("A").seatNumber("1").build());
        return em.persistAndFlush(GameSeat.builder().game(game).seat(seat).build());
    }
}
