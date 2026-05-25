package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요 등록 시")
    @Nested
    class Like {

        @DisplayName("좋아요가 없으면 저장하고 ProductService에 좋아요 수 증가를 위임한다")
        @Test
        void savesAndIncrementsCount_whenNotLikedYet() {
            // given
            ProductModel product = new ProductModel(new BrandModel("L", "감성"), "후드", "포근함", 49_000L);
            when(productService.getById(PRODUCT_ID)).thenReturn(product);
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(false);

            // when
            likeService.like(USER_ID, PRODUCT_ID);

            // then
            verify(likeRepository, times(1)).save(any(LikeModel.class));
            verify(productService, times(1)).incrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("이미 좋아요한 상태면 멱등으로 동작하여 저장도 카운터 증가도 호출되지 않는다")
        @Test
        void doesNothing_whenAlreadyLiked() {
            // given
            ProductModel product = new ProductModel(new BrandModel("L", "감성"), "후드", "포근함", 49_000L);
            when(productService.getById(PRODUCT_ID)).thenReturn(product);
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);

            // when
            likeService.like(USER_ID, PRODUCT_ID);

            // then
            verify(likeRepository, never()).save(any(LikeModel.class));
            verify(productService, never()).incrementLikeCount(anyLong());
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND가 전파되고 저장/카운터 증가가 호출되지 않는다")
        @Test
        void throwsNotFound_andSkipsSave_whenProductDoesNotExist() {
            // given
            when(productService.getById(PRODUCT_ID))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "없음"));

            // when
            CoreException ex = assertThrows(CoreException.class, () -> likeService.like(USER_ID, PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).existsByUserIdAndProductId(anyLong(), anyLong());
            verify(likeRepository, never()).save(any(LikeModel.class));
            verify(productService, never()).incrementLikeCount(anyLong());
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 ProductService에 좋아요 수 감소를 위임한다")
        @Test
        void decrementsCount_whenActuallyDeleted() {
            // given
            when(likeRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(1);

            // when
            likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            verify(productService, times(1)).decrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("삭제할 좋아요가 없으면 멱등으로 동작하여 카운터 감소도 호출되지 않는다")
        @Test
        void doesNothing_whenNothingToDelete() {
            // given
            when(likeRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(0);

            // when
            likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            verify(productService, never()).decrementLikeCount(anyLong());
        }
    }
}
