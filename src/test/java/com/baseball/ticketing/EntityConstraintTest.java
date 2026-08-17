package com.baseball.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseball.ticketing.game.Game;
import com.baseball.ticketing.game.GameSeat;
import com.baseball.ticketing.game.Seat;
import com.baseball.ticketing.game.Section;
import com.baseball.ticketing.game.Stadium;
import com.baseball.ticketing.member.Member;
import com.baseball.ticketing.reservation.Reservation;
import com.baseball.ticketing.reservation.ReservationSeat;
import com.baseball.ticketing.support.AbstractPersistenceTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * ERD/CLAUDE.md에 명시된 DB 제약이 실제 PostgreSQL에 걸려 있는지 검증한다. 특히
 * {@code reservation_seat.game_seat_id} UNIQUE는 좌석 중복 배정을 막는 핵심 제약이며
 * (CLAUDE.md 핵심 설계 원칙 3), 애플리케이션 로직으로 재구현하지 않고 이 제약에 의존한다는
 * 근거가 되는 테스트다.
 */
class EntityConstraintTest extends AbstractPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void member_email_isUnique() {
        em.persistAndFlush(
                Member.builder().email("dup@example.com").password("pw").name("A").build());
        Member duplicate = Member.builder().email("dup@example.com").password("pw").name("B").build();

        // IDENTITY 채번 전략은 persist() 시점에 즉시 INSERT를 실행하므로, 위반은 flush()가 아니라
        // 여기서 발생한다.
        assertUniqueViolation(() -> em.persistAndFlush(duplicate));
    }

    @Test
    void member_createdAt_isSetOnPersist() {
        Member member = em.persistFlushFind(
                Member.builder().email("time@example.com").password("pw").name("A").build());

        assertThat(member.getCreatedAt()).isNotNull();
    }

    @Test
    void seat_sectionRowNumber_isUnique() {
        Stadium stadium = em.persistAndFlush(Stadium.builder().name("잠실야구장").address("서울").build());
        Section section = em.persistAndFlush(Section.builder()
                .stadium(stadium).name("1루 응원석").price(15000).totalSeats(100).build());
        em.persistAndFlush(Seat.builder().section(section).seatRow("A").seatNumber("1").build());
        Seat duplicate = Seat.builder().section(section).seatRow("A").seatNumber("1").build();

        assertUniqueViolation(() -> em.persistAndFlush(duplicate));
    }

    @Test
    void gameSeat_gameAndSeat_isUnique() {
        Stadium stadium = em.persistAndFlush(Stadium.builder().name("잠실야구장").address("서울").build());
        Game game = em.persistAndFlush(Game.builder()
                .stadium(stadium).homeTeam("LG").awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1)).build());
        Section section = em.persistAndFlush(Section.builder()
                .stadium(stadium).name("1루 응원석").price(15000).totalSeats(100).build());
        Seat seat = em.persistAndFlush(
                Seat.builder().section(section).seatRow("A").seatNumber("1").build());
        em.persistAndFlush(GameSeat.builder().game(game).seat(seat).build());
        GameSeat duplicate = GameSeat.builder().game(game).seat(seat).build();

        assertUniqueViolation(() -> em.persistAndFlush(duplicate));
    }

    @Test
    void reservationSeat_gameSeatId_isUnique() {
        Stadium stadium = em.persistAndFlush(Stadium.builder().name("잠실야구장").address("서울").build());
        Game game = em.persistAndFlush(Game.builder()
                .stadium(stadium).homeTeam("LG").awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1)).build());
        Section section = em.persistAndFlush(Section.builder()
                .stadium(stadium).name("1루 응원석").price(15000).totalSeats(100).build());
        Seat seat = em.persistAndFlush(
                Seat.builder().section(section).seatRow("A").seatNumber("1").build());
        GameSeat gameSeat = em.persistAndFlush(GameSeat.builder().game(game).seat(seat).build());

        Member member = em.persistAndFlush(
                Member.builder().email("seatowner@example.com").password("pw").name("A").build());
        Reservation reservation1 = em.persistAndFlush(
                Reservation.builder().member(member).game(game).totalPrice(15000).build());
        Reservation reservation2 = em.persistAndFlush(
                Reservation.builder().member(member).game(game).totalPrice(15000).build());

        em.persistAndFlush(ReservationSeat.builder().reservation(reservation1).gameSeat(gameSeat).build());
        ReservationSeat duplicate =
                ReservationSeat.builder().reservation(reservation2).gameSeat(gameSeat).build();

        assertUniqueViolation(() -> em.persistAndFlush(duplicate));
    }

    private static void assertUniqueViolation(ThrowingRunnable flush) {
        assertThatThrownBy(flush::run)
                .satisfies(ex -> assertThat(rootCause(ex).getMessage())
                        .containsIgnoringCase("duplicate key value violates unique constraint"));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private interface ThrowingRunnable {
        void run();
    }
}
