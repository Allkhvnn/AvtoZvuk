package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository repository;
    private final CurrentActor actors;
    private final Rules rules;
    private final DocumentSupport documents;
    private final StockService stock;
    private final AuditService audit;

    public record Adjustment(long id, String operationType, String reason, Object createdAt,
                             List<Map<String, Object>> items) {}

    @Transactional(timeout = 30)
    public long inventory(Commands.Inventory command) {
        var actor = actors.owner();
        rules.validate(command);
        DocumentSupport.unique(command.items().stream().map(Commands.InventoryItem::productId).toList());
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.INVENTORY, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        stock.lockProducts(command.items().stream().map(Commands.InventoryItem::productId).toList(), false);
        long adjustment = createAdjustment("INVENTORY", command.reason(), command.requestKey(), fingerprint, actor.id());
        var ordered = command.items().stream().sorted(Comparator.comparingLong(Commands.InventoryItem::productId)).toList();
        for (var item : ordered) {
            var lots = repository.lockAvailableLots(item.productId());
            long system = lots.stream().mapToLong(InventoryRepository.StockLot::quantityRemaining).sum();
            long difference = item.actualQuantity() - system;
            if (difference > 0 && item.excessUnitCost() == null)
                throw BusinessException.invalid("Для излишка укажите себестоимость товара: " + item.productId());
            if (difference <= 0 && item.excessUnitCost() != null)
                throw BusinessException.invalid("Себестоимость указывается только для излишка: " + item.productId());
            repository.addAdjustmentItem(adjustment, item.productId(), system, item.actualQuantity(), item.excessUnitCost());
            if (difference > 0) receiveExcess(adjustment, item.productId(), Math.toIntExact(difference),
                    item.excessUnitCost(), command.reason(), actor.id());
            else if (difference < 0) consume(lots, adjustment, item.productId(), Math.toIntExact(-difference),
                    command.reason(), actor.id());
        }
        audit.record(actor.id(), "INVENTORY_POST", "StockAdjustment", adjustment, command.reason());
        return adjustment;
    }

    @Transactional(timeout = 30)
    public long writeOff(Commands.WriteOff command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.WRITE_OFF, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        stock.lockProducts(List.of(command.productId()), false);
        var lots = repository.lockAvailableLots(command.productId());
        long system = lots.stream().mapToLong(InventoryRepository.StockLot::quantityRemaining).sum();
        if (command.quantity() > system) throw BusinessException.conflict("Недостаточно товара для списания");
        long adjustment = createAdjustment("WRITE_OFF", command.reason(), command.requestKey(), fingerprint, actor.id());
        repository.addAdjustmentItem(adjustment, command.productId(), system, system - command.quantity(), null);
        consume(lots, adjustment, command.productId(), command.quantity(), command.reason(), actor.id());
        audit.record(actor.id(), "STOCK_WRITE_OFF", "StockAdjustment", adjustment, command.reason());
        return adjustment;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inventories(int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        return repository.inventories(limit, offset);
    }

    @Transactional(readOnly = true)
    public Adjustment adjustment(long id) {
        actors.owner();
        var header = repository.adjustment(id);
        return new Adjustment(header.id(), header.operationType(), header.reason(), header.createdAt(),
                repository.adjustmentItems(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> movements(long productId, int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        repository.requireProduct(productId);
        return repository.movements(productId, limit, offset);
    }

    private long createAdjustment(String type, String reason, UUID key, String fingerprint, long actor) {
        return repository.createAdjustment(type, reason, key, fingerprint, actor);
    }

    private void receiveExcess(long adjustment, long product, int quantity, BigDecimal cost, String reason, long actor) {
        long lot = repository.createExcessLot(product, adjustment, cost);
        movement(adjustment, product, lot, quantity, reason, actor);
    }

    private void consume(List<InventoryRepository.StockLot> lots, long adjustment, long product, int quantity,
                         String reason, long actor) {
        int needed = quantity;
        for (var lot : lots) {
            int take = (int) Math.min(needed, lot.quantityRemaining());
            if (take > 0) movement(adjustment, product, lot.id(), -take, reason, actor);
            needed -= take;
            if (needed == 0) return;
        }
        throw BusinessException.conflict("Недостаточно товара для корректировки");
    }

    private void movement(long adjustment, long product, long lot, int quantity, String reason, long actor) {
        repository.addMovement(adjustment, product, lot, quantity, reason, actor);
    }
}
