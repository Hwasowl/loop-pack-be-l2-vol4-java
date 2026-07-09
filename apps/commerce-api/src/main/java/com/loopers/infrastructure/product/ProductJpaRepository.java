package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
    boolean existsByIdAndDeletedAtIsNull(Long id);
    long countByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("SELECT p.brandId, COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brandId IN :brandIds GROUP BY p.brandId")
    List<Object[]> countGroupByBrandIdAndDeletedAtIsNull(@Param("brandIds") Collection<Long> brandIds);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    /** 좋아요순 정렬 — product_metrics 조인. product_metrics는 streamer 소유라 native로만 참조한다(매핑 없음). */
    @Query(value = "SELECT p.* FROM product p LEFT JOIN product_metrics pm ON pm.product_id = p.id "
        + "WHERE p.deleted_at IS NULL AND (:brandId IS NULL OR p.brand_id = :brandId) "
        + "ORDER BY COALESCE(pm.like_count, 0) DESC, p.id DESC",
        countQuery = "SELECT COUNT(*) FROM product p "
            + "WHERE p.deleted_at IS NULL AND (:brandId IS NULL OR p.brand_id = :brandId)",
        nativeQuery = true)
    Page<ProductModel> searchOrderByLikes(@Param("brandId") Long brandId, Pageable pageable);

    @Query(value = "SELECT pm.product_id, pm.like_count FROM product_metrics pm WHERE pm.product_id IN :ids", nativeQuery = true)
    List<Object[]> findLikeCountsByProductIds(@Param("ids") Collection<Long> ids);
}
