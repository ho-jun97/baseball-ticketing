package com.baseball.ticketing.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 엔티티의 DB 제약(UNIQUE, {@code @Version} 낙관적 락)을 실제 PostgreSQL(Testcontainers)로 검증하기
 * 위한 베이스 클래스. 로컬에 Docker(Colima 등)가 떠 있어야 실행된다 — 없으면 컨테이너 기동 단계에서
 * 실패한다.
 *
 * <p>"싱글턴 컨테이너" 패턴을 쓴다: {@code POSTGRES}는 이 클래스에 선언된 하나의 static 필드이므로
 * 이를 상속하는 모든 서브클래스(테스트 클래스)가 같은 컨테이너 인스턴스를 공유한다. {@code @Testcontainers}
 * + {@code @Container}를 썼다면 JUnit이 "첫 번째로 실행된 서브클래스"의 테스트가 끝난 뒤 이 공유
 * 컨테이너를 stop() 시켜버려, 같은 JVM에서 실행되는 다음 서브클래스가 이미 죽은 컨테이너에 연결을
 * 시도하다 실패한다 — 정적 초기화 블록에서 한 번만 start()하고 절대 stop()하지 않는 것이 핵심이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractPersistenceTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
