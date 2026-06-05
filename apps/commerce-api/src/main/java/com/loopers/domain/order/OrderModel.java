package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.domain.common.MoneyConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    /** 쿠폰 적용 전 금액 — 항목 subtotal 합계. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount", nullable = false)
    private Money totalAmount;

    /** 쿠폰 할인 금액. 미적용 시 0. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount", nullable = false)
    private Money discountAmount;

    /** 최종 결제 금액 = totalAmount - discountAmount. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "final_amount", nullable = false)
    private Money finalAmount;

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private final List<OrderItem> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        items.forEach(this::addItem);
        this.totalAmount = this.items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.ZERO, Money::add);
        // 항목별 쿠폰 할인의 합. 어떤 쿠폰이 적용됐는지는 각 OrderItem이 보유한다.
        this.discountAmount = this.items.stream()
            .map(OrderItem::getDiscountAmount)
            .reduce(Money.ZERO, Money::add);
        this.finalAmount = this.totalAmount.subtract(this.discountAmount);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void addItem(OrderItem item) {
        this.items.add(item);
        item.assignTo(this);
    }
}
