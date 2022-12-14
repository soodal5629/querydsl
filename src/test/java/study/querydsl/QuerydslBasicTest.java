package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import java.util.List;

// static import
import static com.querydsl.jpa.JPAExpressions.*; // 서브쿼리 시 사용
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
//@Commit ==> 얘 넣으면 테스트하고 ROLLBACK 안됨(데이터 유지)
public class QuerydslBasicTest {
    @PersistenceContext
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach // 각 개별 테스트 실행하기 전에 해당 테스트 메소드 실행
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }
    @Test
    public void startJPQL() {
        // member1을 찾아라.
        Member findMember = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        // QMember m = new QMember("m"); // 어떤 QMember인지 구분하는 m이라는 이름을 주는 것임
        Member findMember = queryFactory.select(member) // Q-Type은 static import 하여 사용하는 것을 권장함
                .from(member)
                .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory.selectFrom(member)
                .where(
                        member.username.eq("member1"), // and 없이도 이렇게 사용 가능
                        member.age.eq(10) // 이 경우 and 로 조회됨
                )
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
        List<Member> fetch = queryFactory.selectFrom(member)
                .fetch();
        Member fetchOne = queryFactory.selectFrom(member)
                .fetchOne();
        Member fetchFirst = queryFactory.selectFrom(member)
                .fetchFirst();
        QueryResults<Member> results = queryFactory.selectFrom(member)
                .fetchResults();
        results.getTotal();
        List<Member> content = results.getResults(); // 컨텐트(데이터)

        long total = queryFactory.selectFrom(member)
                .fetchCount();
    }

    /*
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 오름차순(asc)
     * 단, 2에서 회원 이름이 없으면 마지막에 출력 (nulls test)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory.select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();
        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /*
    * 팀의 이름과 각 팀의 평균 연령을 구하라.
    * */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory.select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team) // JPQL 조인과 동일
                .groupBy(team.name) // 팀의 이름으로 그룹핑
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    @Test
    public void join() {
        List<Member> result = queryFactory.selectFrom(member)
                .join(member.team, team) // inner join과 동일
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /*
     * 세타 조인(연관 관계가 없는 조인)도 가능
     * 회원이 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory.select(member)
                .from(member, team) // 세타 조인은 left join 안됨
                .where(member.username.eq(team.name))
                .fetch(); // 모든 회원과 모든 팀을 다 가져와서 조인해버린 후, where 절에서 필터링
        // cross join 실행됨(교차조인 - 카티션 프로덕트 )

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조회하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA")) // inner join은 where절 쓰면 되므로 left join 할 때만 의미 있음
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
     * 연관관계가 없는 엔티티 외부 조인
     * 회원이 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(team) // 기존에는 leftJoin(member.team, team) => 이렇게 해야 id로 매칭해서 join 함
                .on(member.username.eq(team.name)) // 필터링
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /**
     * 페치 조인 사용
     */
    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();
        Member findMember = queryFactory.selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /*
     * 서브쿼리 - 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member) // 서브쿼리랑 alias 달라야 함
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /*
     * 서브쿼리 - 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member) // 서브쿼리랑 alias 달라야 함
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /*
     * 서브쿼리 - in절 사용
     */
    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member) // 서브쿼리랑 alias 달라야 함
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * select 절에서 서브 쿼리 사용 예제
     */
    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * case문 기초
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory.select(member.age
                            .when(10).then("열살")
                            .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 복잡한 case문 사용 시에는 CaseBuilder 사용해야 함
     */
    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
     * 상수 가져오는 예제
     * */
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A")) // 상수 A
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자열 합치기 concat 예제
     */
    @Test
    public void concat() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void simpleProjection() {
        List<Member> result = queryFactory.select(member)
                .from(member)
                .fetch(); // 이것도 프로젝션 대상이 하나임(Member)
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    /**
     * Tuple 예제
     * 튜플은 querydsl에 종속적이기 때문에 리파지토리 계층에서만 쓰는 것을 권장
     * 추후 하부 기술을 바꾸더라도 서비스, 컨트롤러 계층에 영향 가지 않도록!
     * 따라서 리파지토리에서 서비스, 컨트롤러에 반환할 때는 DTO 사용하길 권장
     */
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory.select(member.username, member.age)
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    /*
    * JPQL로 DTO 조회
    * */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
     * 프로퍼티즈(setter)를 활용한 DTO 조회
     * */
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
     * 필드를 활용한 DTO 조회 - getter, setter 필요없이 필드에 바로 값이 착착 꽂힘
     * */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
     * 생성자를 이용한 DTO 조회
     */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDto() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // DTO 필드명이 다를 때 이렇게 사용하면 됨
                        ExpressionUtils.as( // 서브쿼리 별칭 부여 가능
                                JPAExpressions  // 서브쿼리
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age")
                ))
                .from(member)
                .fetch();
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * BooleanBuilder를 이용한 동적쿼리
     */
    @Test
    public void dynamic_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        if(usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if(ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory.selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * where 다중 파라미터 사용한 동적 쿼리 (메소드 별도 생성)
     */
    @Test
    public void dynamic_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond)) // null이 반환되어 들어가면면 whre절에서 무시되므로 동적 쿼리가 됨
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    /*
     * 조합 가능!!!! (장점)
     * */
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /*
     * 벌크 연산 예제
     */
    @Test
    public void bulkUpdate() {
        // member1, member2 는 28살미만이므로 이름 변경됨
        long count = queryFactory.update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        // ★★★ 벌크연산을 하면 영속성 컨텍스트를 무시하고 바로 db에 쿼리가 날아가기 때문에
        // ★★★ 영속성 컨텍스트와 db 데이터가 서로 상이할 수 있으므로 영속성 컨텍스트 clear 해 줘야 함
        em.flush();
        em.clear();
    }

    /**
     * 모든 회원의 나이를 +1하는 벌크 연산 (-1 넣으면 1살씩 감소)
     */
    @Test
    public void bulkAdd() {
        long count = queryFactory.update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    /**
     * 삭제 벌크 연산
     */
    @Test
    public void bulkDelete() {
        long count = queryFactory.delete(member)
                .where(member.age.gt(10))
                .execute();
    }

    /**
     * sql 함수 사용법 예제
     */
    @Test
    public void sqlFunction() {
        List<String> result = queryFactory.select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory.select(member.username)
                .from(member)
                //.where(member.username.eq(Expressions.stringTemplate("function('lower', {0})",
                //        member.username)))
                // 모든 db에서 제공하는 안시 표준 쿼리에서 제공하는 대표적인 함수들은
                // 기본적으로 내장하고 있기 떄문에 다음과 같이 사용 가능 ex) upper 등등..
               .where(member.username.eq(member.username.lower()))
                .fetch();
    }
}
