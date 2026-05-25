package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long productId, int amount) {
        StockModel stock = stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.decrease(amount);
    }

    @Transactional
    public void increase(Long productId, int amount) {
        StockModel stock = stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.increase(amount);
    }

    @Transactional(readOnly = true)
    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> getQuantities(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (StockModel stock : stockRepository.findAllByProductIdIn(productIds)) {
            result.put(stock.getProductId(), stock.getQuantity());
        }
        return result;
    }
}
