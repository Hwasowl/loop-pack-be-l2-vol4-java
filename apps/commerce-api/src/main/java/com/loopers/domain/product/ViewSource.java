package com.loopers.domain.product;

/**
 * 상품 상세를 <b>어디를 거쳐</b> 열었는지. 랭킹 점수를 매길 때 조회 1건의 값어치가 경로마다 다르기 때문에 남긴다.
 * <p>
 * 특히 {@link #RANKING} — 인기목록을 보고 누른 조회는 <i>랭킹이 스스로 만들어낸 조회</i>다. 이걸 그대로
 * 점수에 넣으면 상위 노출이 더 많은 조회를 부르고, 그 조회가 다시 순위를 올리는 자기참조 고리가 된다.
 * 경로를 남겨두면 나중에 "랭킹 경유 조회는 빼고 다시 계산"이 가능해진다.
 * <p>
 * 클라이언트가 보내는 값이라 위조할 수 있다. 조작을 막는 장치가 아니라, 신호의 질을 높이는 재료다.
 */
public enum ViewSource {

    /** 인기목록·랭킹에서 눌러 들어옴. 랭킹이 만든 조회. */
    RANKING,

    /** 검색 결과에서 들어옴. 사용자가 의도해서 찾은 조회. */
    SEARCH,

    /** 상품 목록·브랜드 페이지 등 서비스 내 다른 경로. */
    BROWSE,

    /** 외부 링크·공유로 유입. 랭킹과 무관한 신호. */
    EXTERNAL,

    /** 경로를 알 수 없음. 클라이언트가 안 보냈거나 모르는 값을 보낸 경우. */
    UNKNOWN;

    /**
     * 요청이 준 값을 경로로 바꾼다. 모르는 값·빈 값은 {@link #UNKNOWN}으로 흡수한다 —
     * 조회는 부가 지표라, 경로를 못 알아들었다고 상세 조회(본 기능)를 400으로 막을 이유가 없다.
     */
    public static ViewSource from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
