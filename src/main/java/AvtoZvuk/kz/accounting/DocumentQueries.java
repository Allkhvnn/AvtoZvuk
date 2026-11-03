package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.dto.trading.*;
import AvtoZvuk.kz.common.BusinessException;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentQueries {
    private final DocumentRepository repository;
    private final TradingMapper mapper;
    private final CurrentActor actors;

    public SaleDocumentResponse sale(long id) {
        var actor = actors.get();
        var header = repository.saleHeader(id);
        if (!actor.owner() && !header.sellerId().equals(actor.employeeId())) throw BusinessException.forbidden();
        return new SaleDocumentResponse(id, header.documentNumber(), header.status(), repository.saleTotal(id),
                repository.saleBonus(id), repository.saleItems(id).stream().map(mapper::saleItem).toList(),
                repository.salePayments(id).stream().map(mapper::payment).toList());
    }

    public PurchaseDocumentResponse purchase(long id) {
        actors.owner();
        var header = repository.purchaseHeader(id);
        return new PurchaseDocumentResponse(id, header.documentNumber(), header.status(),
                repository.purchaseTotal(id), BigDecimal.ZERO,
                repository.purchaseItems(id).stream().map(mapper::purchaseItem).toList(), List.of());
    }

    public ReturnDocumentResponse returned(long id) {
        actors.owner();
        var header = repository.returnHeader(id);
        return new ReturnDocumentResponse(id, header.documentNumber(), header.status(), repository.returnTotal(id),
                repository.returnBonus(id), repository.returnItems(id).stream().map(mapper::returnItem).toList(),
                repository.returnPayments(id).stream().map(mapper::payment).toList());
    }

    public List<SaleSummaryResponse> sales(int limit, int offset) {
        var actor = actors.get();
        page(limit, offset);
        return repository.sales(actor.owner(), actor.employeeId(), limit, offset).stream()
                .map(mapper::saleSummary).toList();
    }

    public List<PurchaseSummaryResponse> purchases(int limit, int offset) {
        actors.owner();
        page(limit, offset);
        return repository.purchases(limit, offset).stream().map(mapper::purchaseSummary).toList();
    }

    private static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw BusinessException.invalid("Некорректная страница");
    }
}
