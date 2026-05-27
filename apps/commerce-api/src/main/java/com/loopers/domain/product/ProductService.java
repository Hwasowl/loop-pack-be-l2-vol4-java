package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductModel getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllByIds(Collection<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable) {
        return productRepository.search(brandId, sort, pageable);
    }

    @Transactional(readOnly = true)
    public long countByBrandId(Long brandId) {
        return productRepository.countByBrandId(brandId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countByBrandIds(Collection<Long> brandIds) {
        return productRepository.countByBrandIds(brandIds);
    }

    /**
     * like_count를 원자적으로 증가시킨다. 동시 like 요청의 lost update를 막기 위해
     * 도메인 메서드({@link ProductModel#increaseLike()}) 대신 단일 UPDATE 쿼리를 사용한다.
     * 카운터는 약한 일관성(D3)이므로 도메인 메서드 우회를 허용한다.
     */
    @Transactional
    public void incrementLikeCount(Long productId) {
        if (productRepository.incrementLikeCount(productId) == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
    }

    /**
     * like_count를 원자적으로 감소시킨다. 이미 0이면 SQL 조건(like_count > 0)으로 자연 멱등 통과한다.
     * 호출자가 상품 존재를 사전에 검증한다는 전제(LikeFacade 흐름).
     */
    @Transactional
    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }
}
