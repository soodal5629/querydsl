package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberDto {

    private String username;
    private int age;

    @QueryProjection // 이거 추가 후, gradle => compileQuerydsl 누르면 dto도 Q 파일로 생성됨
    // 편리하긴 하나, 해당 DTO가 Querydsl에 대한 의존성을 갖게 되어 버림(단점,,)
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
