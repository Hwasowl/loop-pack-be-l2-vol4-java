package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
    long countByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("SELECT p.brandId, COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brandId IN :brandIds GROUP BY p.brandId")
    List<Object[]> countGroupByBrandIdAndDeletedAtIsNull(@Param("brandIds") Collection<Long> brandIds);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id AND p.deletedAt IS NULL")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0 AND p.deletedAt IS NULL")
    int decrementLikeCount(@Param("id") Long id);
}
