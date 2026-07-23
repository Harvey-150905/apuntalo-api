package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.dto.ProductSalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto;
import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Payment;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.Ticket;
import com.harbeyescala.api_apuntalo.entity.TicketLine;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import com.harbeyescala.api_apuntalo.exception.BadRequestException;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import com.harbeyescala.api_apuntalo.service.ActiveStoreContext;
import com.harbeyescala.api_apuntalo.service.BusinessTimeService;
import com.harbeyescala.api_apuntalo.service.ReportDateRangePolicy;
import com.harbeyescala.api_apuntalo.service.TicketService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:store_scope;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class TicketStoreScopeRepositoryTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 1, 0, 0);

    @Autowired private EntityManager entityManager;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketLineRepository ticketLineRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Negocio tenantOne;
    private Negocio tenantTwo;
    private Store storeA;
    private Store storeB;
    private Store otherTenantStore;
    private User userA;
    private User userB;
    private User otherTenantUser;
    private Ticket paidAOne;
    private Ticket paidATwo;
    private Ticket paidB;
    private Ticket paidOtherTenant;
    private Ticket openA;
    private Ticket openB;
    private Ticket cancelledA;
    private Ticket cancelledB;

    @BeforeEach
    void setUp() {
        tenantOne = persistTenant("Tenant one");
        tenantTwo = persistTenant("Tenant two");
        storeA = persistStore(tenantOne, "Store A", "A", true);
        storeB = persistStore(tenantOne, "Store B", "B", false);
        otherTenantStore = persistStore(tenantTwo, "Other tenant", "C", true);
        userA = persistUser(tenantOne, storeA, "user-a");
        userB = persistUser(tenantOne, storeB, "user-b");
        otherTenantUser = persistUser(tenantTwo, otherTenantStore, "user-c");

        Mesa mesaA = persistMesa(tenantOne, storeA, 1);
        Mesa mesaB = persistMesa(tenantOne, storeB, 1);
        Mesa otherMesa = persistMesa(tenantTwo, otherTenantStore, 1);

        paidAOne = persistTicket(tenantOne, storeA, mesaA, userA, TicketStatus.PAID,
                "10.00", LocalDateTime.of(2026, 7, 10, 10, 0));
        paidATwo = persistTicket(tenantOne, storeA, mesaA, userA, TicketStatus.PAID,
                "20.00", LocalDateTime.of(2026, 7, 11, 10, 0));
        paidB = persistTicket(tenantOne, storeB, mesaB, userB, TicketStatus.PAID,
                "100.00", LocalDateTime.of(2026, 7, 12, 10, 0));
        paidOtherTenant = persistTicket(tenantTwo, otherTenantStore, otherMesa, otherTenantUser,
                TicketStatus.PAID, "1000.00", LocalDateTime.of(2026, 7, 13, 10, 0));
        paidAOne.setCommercialNumber(1L);
        paidATwo.setCommercialNumber(2L);
        paidB.setCommercialNumber(1L);
        paidOtherTenant.setCommercialNumber(1L);

        openA = persistTicket(tenantOne, storeA, mesaA, userA, TicketStatus.OPEN,
                "5.00", LocalDateTime.of(2026, 7, 14, 10, 0));
        openB = persistTicket(tenantOne, storeB, mesaB, userB, TicketStatus.OPEN,
                "50.00", LocalDateTime.of(2026, 7, 15, 10, 0));
        cancelledA = persistTicket(tenantOne, storeA, mesaA, userA, TicketStatus.CANCELLED,
                "7.00", LocalDateTime.of(2026, 7, 16, 10, 0));
        cancelledB = persistTicket(tenantOne, storeB, mesaB, userB, TicketStatus.CANCELLED,
                "70.00", LocalDateTime.of(2026, 7, 17, 10, 0));

        persistLine(paidAOne, tenantOne, storeA, 101L, "Coffee A", "10.00");
        persistLine(paidATwo, tenantOne, storeA, 102L, "Cake A", "20.00");
        persistLine(paidB, tenantOne, storeB, 201L, "Coffee B", "100.00");
        persistLine(paidOtherTenant, tenantTwo, otherTenantStore, 301L, "Other", "1000.00");

        persistPayment(paidAOne, tenantOne, storeA, userA, PaymentMethod.CASH, "10.00");
        persistPayment(paidATwo, tenantOne, storeA, userA, PaymentMethod.CARD, "20.00");
        persistPayment(paidB, tenantOne, storeB, userB, PaymentMethod.CASH, "100.00");
        persistPayment(paidOtherTenant, tenantTwo, otherTenantStore, otherTenantUser,
                PaymentMethod.CARD, "1000.00");
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void pagedHistoriesAreScopedByTenantAndActiveStore() {
        var paidPage = ticketRepository
                .findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantOne.getId(), storeA.getId(), TicketStatus.PAID, FROM, TO,
                        page("paidAt"));
        var openPage = ticketRepository.findByNegocioIdAndStoreIdAndStatusOrderByCreatedAtDesc(
                tenantOne.getId(), storeA.getId(), TicketStatus.OPEN, page("createdAt"));
        var cancelledPage = ticketRepository.findByNegocioIdAndStoreIdAndStatusOrderByUpdatedAtDesc(
                tenantOne.getId(), storeA.getId(), TicketStatus.CANCELLED, page("updatedAt"));

        assertThat(ids(paidPage.getContent())).containsExactly(paidATwo.getId(), paidAOne.getId());
        assertThat(ids(openPage.getContent())).containsExactly(openA.getId());
        assertThat(ids(cancelledPage.getContent())).containsExactly(cancelledA.getId());

        var storeBPage = ticketRepository
                .findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantOne.getId(), storeB.getId(), TicketStatus.PAID, FROM, TO,
                        page("paidAt"));
        var otherTenantPage = ticketRepository
                .findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantTwo.getId(), otherTenantStore.getId(), TicketStatus.PAID, FROM, TO,
                        page("paidAt"));

        assertThat(ids(storeBPage.getContent())).containsExactly(paidB.getId());
        assertThat(ids(otherTenantPage.getContent())).containsExactly(paidOtherTenant.getId());
    }

    @Test
    void reportsCountsAndDailyInputAreScopedByTenantAndActiveStore() {
        List<UserSalesSummaryDto> userSales =
                ticketRepository.findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
                        tenantOne.getId(), storeA.getId(), TicketStatus.PAID, FROM, TO);
        List<ProductSalesSummaryDto> productSales = ticketLineRepository.findProductSalesSummary(
                tenantOne.getId(), storeA.getId(), TicketStatus.PAID, TicketLineStatus.ACTIVE,
                FROM, TO);
        List<Ticket> dailyInput =
                ticketRepository.findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantOne.getId(), storeA.getId(), TicketStatus.PAID, FROM, TO);

        assertThat(userSales).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(userA.getId());
            assertThat(row.getTotalSales()).isEqualByComparingTo("30.00");
        });
        assertThat(productSales).extracting(ProductSalesSummaryDto::getProductName)
                .containsExactlyInAnyOrder("Coffee A", "Cake A");
        assertThat(productSales).extracting(ProductSalesSummaryDto::getTotalSales)
                .containsExactlyInAnyOrder(new BigDecimal("10.00"), new BigDecimal("20.00"));
        assertThat(ids(dailyInput)).containsExactly(paidATwo.getId(), paidAOne.getId());

        BigDecimal storeATotal = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                tenantOne.getId(), storeA.getId(), TicketStatus.PAID, FROM, TO);
        Long storeAPaidCount =
                ticketRepository.countByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                        tenantOne.getId(), storeA.getId(), TicketStatus.PAID, FROM, TO);
        Long storeACancelledCount =
                ticketRepository.countByNegocioIdAndStoreIdAndStatusAndCancelledAtGreaterThanEqualAndCancelledAtLessThan(
                        tenantOne.getId(), storeA.getId(), TicketStatus.CANCELLED, FROM, TO);
        BigDecimal storeACash = paymentRepository.sumAmount(tenantOne.getId(), storeA.getId(),
                TicketStatus.PAID, PaymentMethod.CASH, FROM, TO);
        BigDecimal storeACard = paymentRepository.sumAmount(tenantOne.getId(), storeA.getId(),
                TicketStatus.PAID, PaymentMethod.CARD, FROM, TO);

        assertThat(storeATotal).isEqualByComparingTo("30.00");
        assertThat(storeAPaidCount).isEqualTo(2L);
        assertThat(storeATotal.divide(BigDecimal.valueOf(storeAPaidCount)))
                .isEqualByComparingTo("15.00");
        assertThat(storeACancelledCount).isEqualTo(1L);
        assertThat(storeACash).isEqualByComparingTo("10.00");
        assertThat(storeACard).isEqualByComparingTo("20.00");

        assertThat(ticketRepository.countByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                tenantOne.getId(), storeB.getId(), TicketStatus.PAID, FROM, TO)).isEqualTo(1L);
        assertThat(ticketRepository.countByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                tenantTwo.getId(), otherTenantStore.getId(), TicketStatus.PAID, FROM, TO)).isEqualTo(1L);
    }

    @Test
    void ticketServiceHistoriesAndReportsUseTheActiveStoreEndToEnd() {
        CurrentUser currentUser = mock(CurrentUser.class);
        ActiveStoreContext storeContext = mock(ActiveStoreContext.class);
        when(currentUser.getTenantId()).thenReturn(tenantOne.getId());
        when(storeContext.storeId()).thenReturn(storeA.getId());
        when(storeContext.requireStore()).thenReturn(storeA);
        BusinessTimeService businessTime = new BusinessTimeService(
                storeContext, Clock.systemUTC(), "Europe/Madrid");
        ReportDateRangePolicy dateRangePolicy = new ReportDateRangePolicy(366);

        TicketService service = new TicketService(
                ticketRepository, null, null, null, ticketLineRepository, null, null,
                paymentRepository, null, null, null, null, currentUser,
                storeContext, businessTime, dateRangePolicy);

        var paidHistory = service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 20,
                null, null, null, null);
        assertThat(paidHistory.getContent())
                .extracting("id").containsExactly(paidATwo.getId(), paidAOne.getId());
        assertThat(paidHistory.getContent().get(0)).satisfies(dto -> {
            assertThat(dto.getCommercialNumber()).isEqualTo(2L);
            assertThat(dto.getCommercialNumberFormatted()).isEqualTo("000002");
            assertThat(dto.getMesaId()).isNotNull();
            assertThat(dto.getPaidById()).isEqualTo(userA.getId());
            assertThat(dto.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        });
        assertThat(service.findOpenTicketsPaged(0, 20).getContent())
                .extracting("id").containsExactly(openA.getId());
        assertThat(service.findCancelledTicketsPaged(0, 20).getContent())
                .extracting("id").containsExactly(cancelledA.getId());

        assertThat(service.getUserSalesSummary(FROM.toLocalDate(), TO.minusDays(1).toLocalDate()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getUserId()).isEqualTo(userA.getId());
                    assertThat(row.getTotalSales()).isEqualByComparingTo("30.00");
                });
        assertThat(service.getProductSalesSummary(FROM.toLocalDate(), TO.minusDays(1).toLocalDate()))
                .extracting(ProductSalesSummaryDto::getProductName)
                .containsExactlyInAnyOrder("Coffee A", "Cake A");
        assertThat(service.getDailySalesSummary(FROM.toLocalDate(), TO.minusDays(1).toLocalDate()))
                .filteredOn(day -> day.getTicketCount() > 0)
                .extracting("ticketCount", "totalSales")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, new BigDecimal("10.00")),
                        org.assertj.core.groups.Tuple.tuple(1L, new BigDecimal("20.00")));

        var average = service.getAverageTicketSummary(FROM.toLocalDate(), TO.minusDays(1).toLocalDate());
        assertThat(average.getTicketCount()).isEqualTo(2L);
        assertThat(average.getTotalSales()).isEqualByComparingTo("30.00");
        assertThat(average.getAverageTicket()).isEqualByComparingTo("15.00");

        var closing = service.getCashClosingSummary(FROM.toLocalDate(), TO.minusDays(1).toLocalDate());
        assertThat(closing.getTotalSales()).isEqualByComparingTo("30.00");
        assertThat(closing.getCashTotal()).isEqualByComparingTo("10.00");
        assertThat(closing.getCardTotal()).isEqualByComparingTo("20.00");
        assertThat(closing.getPaidTickets()).isEqualTo(2L);
        assertThat(closing.getCancelledTickets()).isEqualTo(1L);

        assertThatThrownBy(() -> service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), -1, 20,
                null, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 101,
                null, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 20,
                null, 0L, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo("INVALID_USER_ID"));
        assertThatThrownBy(() -> service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 20,
                null, null, -1L, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 20,
                null, null, null, 0L)).isInstanceOf(BusinessRuleException.class);

        when(storeContext.storeId()).thenReturn(storeB.getId());
        when(storeContext.requireStore()).thenReturn(storeB);
        assertThat(service.findPaidTicketsPaged(
                FROM.toLocalDate(), TO.minusDays(1).toLocalDate(), 0, 20,
                null, null, null, null)
                .getContent()).extracting("id").containsExactly(paidB.getId());
    }

    @Test
    void paidHistoryIncludesInclusiveStartAndLastSecondButExcludesExclusiveEnd() {
        Mesa mesa = persistMesa(tenantOne, storeA, 99);
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime toExclusive = LocalDateTime.of(2026, 9, 2, 0, 0);

        Ticket before = persistTicket(tenantOne, storeA, mesa, userA, TicketStatus.PAID,
                "1.00", from.minusSeconds(1));
        Ticket atStart = persistTicket(tenantOne, storeA, mesa, userA, TicketStatus.PAID,
                "2.00", from);
        Ticket lastSecond = persistTicket(tenantOne, storeA, mesa, userA, TicketStatus.PAID,
                "3.00", toExclusive.minusSeconds(1));
        Ticket atExclusiveEnd = persistTicket(tenantOne, storeA, mesa, userA, TicketStatus.PAID,
                "4.00", toExclusive);
        entityManager.flush();
        entityManager.clear();

        List<Ticket> result = ticketRepository
                .findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantOne.getId(), storeA.getId(), TicketStatus.PAID, from, toExclusive);

        assertThat(ids(result)).containsExactly(lastSecond.getId(), atStart.getId());
        assertThat(ids(result)).doesNotContain(before.getId(), atExclusiveEnd.getId());
    }

    @Test
    void paidHistoryCombinesOptionalFiltersWithoutDuplicatesOrScopeLeaks() {
        User secondStoreAUser = persistUser(tenantOne, storeA, "user-a-2");
        Mesa firstMesa = persistMesa(tenantOne, storeA, 70);
        Mesa secondMesa = persistMesa(tenantOne, storeA, 71);
        Mesa storeBMesa = persistMesa(tenantOne, storeB, 70);
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 10, 12, 0);

        Ticket cash = persistTicket(tenantOne, storeA, firstMesa, userA,
                TicketStatus.PAID, "10.00", paidAt);
        cash.setCommercialNumber(42L);
        cash.setPaymentMethod(PaymentMethod.CASH);
        Ticket card = persistTicket(tenantOne, storeA, secondMesa, secondStoreAUser,
                TicketStatus.PAID, "20.00", paidAt);
        card.setCommercialNumber(43L);
        card.setPaymentMethod(PaymentMethod.CARD);
        Ticket mixed = persistTicket(tenantOne, storeA, firstMesa, userA,
                TicketStatus.PAID, "30.00", paidAt);
        mixed.setCommercialNumber(44L);
        mixed.setPaymentMethod(PaymentMethod.MIXED);
        Ticket sameNumberOtherStore = persistTicket(tenantOne, storeB, storeBMesa, userB,
                TicketStatus.PAID, "100.00", paidAt);
        sameNumberOtherStore.setCommercialNumber(42L);
        sameNumberOtherStore.setPaymentMethod(PaymentMethod.CASH);

        persistLine(mixed, tenantOne, storeA, 440L, "Mixed snapshot", "30.00");
        persistPayment(cash, tenantOne, storeA, userA, PaymentMethod.CASH, "10.00");
        persistPayment(card, tenantOne, storeA, secondStoreAUser, PaymentMethod.CARD, "20.00");
        persistPayment(mixed, tenantOne, storeA, userA, PaymentMethod.CASH, "10.00");
        persistPayment(mixed, tenantOne, storeA, userA, PaymentMethod.CARD, "20.00");
        persistPayment(sameNumberOtherStore, tenantOne, storeB, userB, PaymentMethod.CASH, "100.00");
        entityManager.flush();
        entityManager.clear();

        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 11, 0, 0);

        Page<Ticket> unfiltered = searchHistory(from, to, null, null, null, null);
        assertThat(ids(unfiltered.getContent()))
                .containsExactly(mixed.getId(), card.getId(), cash.getId());
        assertThat(unfiltered.getTotalElements()).isEqualTo(3);

        assertThat(ids(searchHistory(from, to, PaymentMethod.CASH, null, null, null).getContent()))
                .containsExactly(cash.getId());
        assertThat(ids(searchHistory(from, to, PaymentMethod.CARD, null, null, null).getContent()))
                .containsExactly(card.getId());

        Page<Ticket> mixedPage = searchHistory(
                from, to, PaymentMethod.MIXED, null, null, null);
        assertThat(ids(mixedPage.getContent())).containsExactly(mixed.getId());
        assertThat(mixedPage.getTotalElements()).isEqualTo(1);

        assertThat(ids(searchHistory(from, to, null, userA.getId(), null, null).getContent()))
                .containsExactly(mixed.getId(), cash.getId());
        assertThat(searchHistory(from, to, null, userB.getId(), null, null)).isEmpty();
        assertThat(ids(searchHistory(from, to, null, null, firstMesa.getId(), null).getContent()))
                .containsExactly(mixed.getId(), cash.getId());
        assertThat(searchHistory(from, to, null, null, storeBMesa.getId(), null)).isEmpty();
        assertThat(ids(searchHistory(from, to, null, null, null, 42L).getContent()))
                .containsExactly(cash.getId());

        Page<Ticket> combined = searchHistory(
                from, to, PaymentMethod.MIXED, userA.getId(), firstMesa.getId(), 44L);
        assertThat(ids(combined.getContent())).containsExactly(mixed.getId());
        assertThat(combined.getTotalElements()).isEqualTo(1);
    }

    private PageRequest page(String timestampProperty) {
        return PageRequest.of(0, 20, Sort.by(Sort.Order.desc(timestampProperty), Sort.Order.desc("id")));
    }

    private Page<Ticket> searchHistory(
            LocalDateTime from,
            LocalDateTime to,
            PaymentMethod paymentMethod,
            Long userId,
            Long mesaId,
            Long commercialNumber
    ) {
        return ticketRepository.searchPaidHistory(
                tenantOne.getId(),
                storeA.getId(),
                TicketStatus.PAID,
                from,
                to,
                paymentMethod,
                userId,
                mesaId,
                commercialNumber,
                page("paidAt")
        );
    }

    private List<Long> ids(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::getId).toList();
    }

    private Negocio persistTenant(String name) {
        Negocio tenant = Negocio.builder().nombre(name).activo(true)
                .cashReconciliationEnabled(false).build();
        entityManager.persist(tenant);
        return tenant;
    }

    private Store persistStore(Negocio tenant, String name, String code, boolean primary) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Store store = Store.builder().negocio(tenant).name(name)
                .normalizedName(name.toLowerCase()).code(code).timezone("Europe/Madrid")
                .active(true).primaryStore(primary).countryCode("ES")
                .cashReconciliationEnabled(false).createdAt(now).updatedAt(now).build();
        entityManager.persist(store);
        return store;
    }

    private User persistUser(Negocio tenant, Store defaultStore, String username) {
        User user = User.builder().nombre(username).username(username).password("hash")
                .role(Role.ADMIN).negocio(tenant).defaultStore(defaultStore)
                .activo(true).tokenVersion(1).build();
        entityManager.persist(user);
        return user;
    }

    private Mesa persistMesa(Negocio tenant, Store store, int number) {
        Mesa mesa = Mesa.builder().numero(number).status(MesaStatus.FREE).activa(true)
                .negocio(tenant).store(store).build();
        entityManager.persist(mesa);
        return mesa;
    }

    private Ticket persistTicket(Negocio tenant, Store store, Mesa mesa, User actor,
                                 TicketStatus status, String total, LocalDateTime timestamp) {
        Ticket ticket = Ticket.builder().status(status).total(new BigDecimal(total))
                .createdAt(timestamp).updatedAt(timestamp).paidAt(status == TicketStatus.PAID ? timestamp : null)
                .cancelledAt(status == TicketStatus.CANCELLED ? timestamp : null)
                .paymentMethod(status == TicketStatus.PAID ? PaymentMethod.CARD : null)
                .mesa(mesa).negocio(tenant).store(store).createdBy(actor)
                .paidBy(status == TicketStatus.PAID ? actor : null)
                .cancelledBy(status == TicketStatus.CANCELLED ? actor : null)
                .originSessionLegacy(true).build();
        entityManager.persist(ticket);
        return ticket;
    }

    private void persistLine(Ticket ticket, Negocio tenant, Store store, Long productId,
                             String productName, String subtotal) {
        TicketLine line = TicketLine.builder().ticket(ticket).negocio(tenant).store(store)
                .productId(productId).productNameSnapshot(productName)
                .unitPriceSnapshot(new BigDecimal(subtotal)).quantity(1)
                .subtotal(new BigDecimal(subtotal)).subtotalBeforeDiscount(new BigDecimal(subtotal))
                .discountPercentage(0).discountAmount(BigDecimal.ZERO).batchNumber(1)
                .status(TicketLineStatus.ACTIVE).createdAt(ticket.getCreatedAt()).build();
        entityManager.persist(line);
    }

    private void persistPayment(Ticket ticket, Negocio tenant, Store store, User actor,
                                PaymentMethod method, String amount) {
        LocalDateTime paidAt = ticket.getPaidAt();
        Payment payment = Payment.builder().negocioId(tenant.getId()).store(store).ticket(ticket)
                .method(method).amount(new BigDecimal(amount))
                .cashReceived(method == PaymentMethod.CASH ? new BigDecimal(amount) : null)
                .changeGiven(method == PaymentMethod.CASH ? BigDecimal.ZERO.setScale(2) : null)
                .paidBy(actor).paidAt(paidAt).legacyImported(false).sessionLegacy(true)
                .createdAt(paidAt).build();
        entityManager.persist(payment);
    }
}
