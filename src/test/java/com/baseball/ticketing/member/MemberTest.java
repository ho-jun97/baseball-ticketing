package com.baseball.ticketing.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void builder_withoutRole_defaultsToUser() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("secret")
                .name("홍길동")
                .phone("010-0000-0000")
                .build();

        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    void builder_withExplicitRole_keepsGivenRole() {
        Member admin = Member.builder()
                .email("admin@example.com")
                .password("secret")
                .name("관리자")
                .role(MemberRole.ADMIN)
                .build();

        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
    }
}
