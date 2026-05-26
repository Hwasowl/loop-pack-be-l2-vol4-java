package com.loopers.interfaces.api;

/**
 * 페이지네이션 정책 상수.
 *
 * <p>모든 목록 조회 엔드포인트의 {@code size} 파라미터 상한.
 * 거대한 size 요청으로 인한 부하·메모리 폭증을 차단한다. (명세 §5)</p>
 */
public final class PageSize {

    /** size 파라미터 최대값. */
    public static final int MAX = 100;

    private PageSize() {}
}
