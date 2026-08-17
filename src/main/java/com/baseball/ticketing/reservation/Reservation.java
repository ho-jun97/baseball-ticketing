package com.baseball.ticketing.reservation;

import com.baseball.ticketing.game.Game;
import com.baseball.ticketing.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Builder
    public Reservation(Member member, Game game, int totalPrice) {
        this.member = member;
        this.game = game;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** PENDING → CONFIRMED. 결제 승인 시 호출 (POST /reservations/{id}/payments). */
    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 예매는 확정할 수 없습니다: reservationId=" + id);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /** PENDING/CONFIRMED → CANCELED. DELETE /reservations/{id}에서 호출. */
    public void cancel() {
        if (this.status == ReservationStatus.CANCELED || this.status == ReservationStatus.EXPIRED) {
            throw new IllegalStateException("이미 종료된 예매입니다: reservationId=" + id);
        }
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    /** PENDING → EXPIRED. HOLD TTL 만료 스케줄러(4주차 구현)에서 호출. */
    public void expire() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 예매는 만료 처리할 수 없습니다: reservationId=" + id);
        }
        this.status = ReservationStatus.EXPIRED;
    }
}
