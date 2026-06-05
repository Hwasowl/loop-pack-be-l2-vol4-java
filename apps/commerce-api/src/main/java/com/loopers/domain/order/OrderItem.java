package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.common.MoneyConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/** 주문 시점 상품 스냅샷. Order의 불변 구성요소(같은 애그리거트)라 BaseEntity 미상속. Product·IssuedCoupon은 다른 애그리거트라 ID 참조. */
@Getter
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price", nullable = false)
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 이 항목에 적용된 발급 쿠폰 id. 미적용 시 null. 쿠폰 → 주문 역추적의 키. */
    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    /** 이 항목에 적용된 쿠폰 할인액. 미적용 시 0. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount", nullable = false)
    private Money discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected OrderItem() {}

    /** 쿠폰 미적용 항목. */
    public OrderItem(Long productId, String productName, Long unitPrice, Integer quantity) {
        this(productId, productName, unitPrice, quantity, null, Money.ZERO);
    }

    public OrderItem(Long productId, String productName, Long unitPrice, Integer quantity, Long issuedCouponId, Money discountAmount) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "제품 ID는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목의 상품명은 비어있을 수 없습니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1개 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = Money.of(unitPrice);
        this.quantity = quantity;
        this.issuedCouponId = issuedCouponId;
        this.discountAmount = discountAmount == null ? Money.ZERO : discountAmount;
    }

    /** 패키지-비공개 — {@link OrderModel#addItem(OrderItem)}만 호출. 외부에서 부모를 바꾸지 못하게 막는다. */
    void assignTo(OrderModel order) {
        this.order = order;
    }

    /** 할인 전 금액. */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    /** 쿠폰 할인 후 결제 금액. 할인액이 subtotal을 초과하면 Money가 BAD_REQUEST로 막는다. */
    public Money payable() {
        return subtotal().subtract(discountAmount);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
