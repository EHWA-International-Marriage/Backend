package com.ewha.backend.entity._common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 도메인이 공통으로 쓰는 국가. 엔티티에서는 @Enumerated(EnumType.STRING)으로 저장한다.
 */
@Getter
@RequiredArgsConstructor
public enum Country {

    UNDECIDED("미정"),
    UZBEKISTAN("우즈베키스탄"),
    KYRGYZSTAN("키르기스스탄"),
    KAZAKHSTAN("카자흐스탄"),
    RUSSIA("러시아"),
    CHINA("중국"),
    VIETNAM("베트남");

    private final String displayName;
}
