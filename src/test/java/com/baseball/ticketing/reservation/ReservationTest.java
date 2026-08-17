package com.baseball.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseball.ticketing.game.Game;
import com.baseball.ticketing.game.Stadium;
import com.baseball.ticketing.member.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private Reservation newPendingReservation() {
        Member member = Member.builder().email("user@example.com").password("pw").name("홍길동").build();
        Stadium stadium = Stadium.builder().name("잠실야구장").address("서울").build();
        Game game = Game.builder()
                .stadium(stadium)
                .homeTeam("LG")
                .awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1))
                .build();
        return Reservation.builder().member(member).game(game).totalPrice(30000).build();
    }

    @Test
    void builder_defaultsStatusToPending() {
        assertThat(newPendingReservation().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void confirm_fromPending_movesToConfirmedAndStampsConfirmedAt() {
        Reservation reservation = newPendingReservation();

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirm_whenNotPending_throws() {
        Reservation reservation = newPendingReservation();
        reservation.confirm();

        assertThatThrownBy(reservation::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_fromPending_movesToCanceledAndStampsCanceledAt() {
        Reservation reservation = newPendingReservation();

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(reservation.getCanceledAt()).isNotNull();
    }

    @Test
    void cancel_fromConfirmed_movesToCanceled() {
        Reservation reservation = newPendingReservation();
        reservation.confirm();

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    void cancel_whenAlreadyCanceled_throws() {
        Reservation reservation = newPendingReservation();
        reservation.cancel();

        assertThatThrownBy(reservation::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_whenAlreadyExpired_throws() {
        Reservation reservation = newPendingReservation();
        reservation.expire();

        assertThatThrownBy(reservation::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expire_fromPending_movesToExpired() {
        Reservation reservation = newPendingReservation();

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void expire_whenNotPending_throws() {
        Reservation reservation = newPendingReservation();
        reservation.confirm();

        assertThatThrownBy(reservation::expire).isInstanceOf(IllegalStateException.class);
    }
}
