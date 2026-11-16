package com.ewha.backend.entity._common;

import java.util.Objects;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import org.hibernate.Hibernate;

@Getter
@MappedSuperclass
public abstract class IdentifiableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프록시와 실제 엔티티를 같은 것으로 비교하고, 저장 전(id == null) 엔티티는 자기 자신과만 같다.
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        IdentifiableEntity that = (IdentifiableEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    // 저장 전후로 값이 바뀌지 않도록 id 대신 클래스 기준 해시를 쓴다.
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
