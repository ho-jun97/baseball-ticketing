package com.baseball.ticketing.reservation;

import com.baseball.ticketing.game.GameSeat;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예매-좌석 매핑(N:M 해소). {@code game_seat_id} UNIQUE 제약으로 좌석 중복 배정을 막는다
 * (CLAUDE.md 핵심 설계 원칙 3) — 애플리케이션 로직으로 이 제약을 재구현하지 않는다.
 */
@Entity
@Table(name = "reservation_seat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_seat_id", nullable = false, unique = true)
    private GameSeat gameSeat;

    @Builder
    public ReservationSeat(Reservation reservation, GameSeat gameSeat) {
        this.reservation = reservation;
        this.gameSeat = gameSeat;
    }
}
