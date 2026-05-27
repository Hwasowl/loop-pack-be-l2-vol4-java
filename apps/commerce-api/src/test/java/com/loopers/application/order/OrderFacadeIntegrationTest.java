package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderFailureReason;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserName;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long product1Id;
    private Long product2Id;

    @BeforeEach
    void setUp() {
        UserModel user = userRepository.save(new UserModel(
            new LoginId("loopers01"), "$2a$10$dummyEncodedHash",
            new UserName("홍길동"), LocalDate.of(2002, 5, 11), new Email("test@loopers.com")
        ));
        userId = user.getId();

        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel p1 = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L));
        ProductModel p2 = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L));
        product1Id = p1.getId();
        product2Id = p2.getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성 시")
    @Nested
    class PlaceOrder {

        @DisplayName("정상 주문이면 status=SUCCEEDED로 종료되고 재고 차감 + items가 orderId로 영속된다")
        @Test
        void persistsOrderAsSucceeded_andDecreasesStock() {
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                new OrderLineCommand(product1Id, 2),
                new OrderLineCommand(product2Id, 1)
            ));

            assertAll(
                () -> assertThat(info.status()).isEqualTo(OrderStatus.SUCCEEDED),
                () -> assertThat(info.totalAmount()).isEqualTo(50_000L * 2 + 30_000L),
                () -> assertThat(info.items()).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(8),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(4),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                () -> assertThat(orderItemJpaRepository.findAllByOrderId(info.id())).hasSize(2)
            );
        }

        @DisplayName("존재하지 않는 유저로 주문하면 NOT_FOUND이고 Order row도 OrderItem도 생기지 않는다")
        @Test
        void throwsNotFound_andNoRows_whenUserDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(99_999L, List.of(new OrderLineCommand(product1Id, 1)))
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(orderItemJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 상품을 포함하면 NOT_FOUND이고 Order row도 OrderItem도 생기지 않는다")
        @Test
        void throwsNotFound_andNoRows_whenProductDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    new OrderLineCommand(product1Id, 1),
                    new OrderLineCommand(99_999L, 1)
                ))
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(orderItemJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("재고 부족이면 CONFLICT이고 재고는 롤백되지만 Order는 FAILED + items는 잔존한다")
        @Test
        void throwsConflict_andLeavesFailedOrderRow_whenStockIsInsufficient() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    new OrderLineCommand(product1Id, 1),
                    new OrderLineCommand(product2Id, 10)
                ))
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(orderJpaRepository.count()).isEqualTo(1);

            OrderModel failed = orderJpaRepository.findAll().get(0);
            assertAll(
                () -> assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(failed.getFailureReason()).isEqualTo(OrderFailureReason.STOCK_SHORTAGE),
                () -> assertThat(orderItemJpaRepository.findAllByOrderId(failed.getId())).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("같은 상품을 여러 line으로 보내도 합산된 수량으로 재고가 한 번에 차감된다")
        @Test
        void aggregatesSameProductLines_forStockDecrease() {
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                new OrderLineCommand(product1Id, 2),
                new OrderLineCommand(product1Id, 3)
            ));

            assertAll(
                () -> assertThat(info.status()).isEqualTo(OrderStatus.SUCCEEDED),
                () -> assertThat(info.items()).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }
    }
}
