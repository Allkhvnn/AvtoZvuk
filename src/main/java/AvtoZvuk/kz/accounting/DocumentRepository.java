package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.BusinessException;
import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository @RequiredArgsConstructor
public class DocumentRepository {
    private final Database db;
    record Header(long id,String documentNumber,String status,Long sellerId) {}
    record SaleItemRow(long id,long productId,String productName,String productSku,int quantity,BigDecimal unitPrice,
                       BigDecimal discountAmount,BigDecimal lineTotal,int returnedQuantity,BigDecimal returnedAmount) {}
    record PurchaseItemRow(long id,long productId,int quantity,BigDecimal unitCost,BigDecimal lineTotal) {}
    record ReturnItemRow(long id,long saleItemId,int quantity,BigDecimal refundAmount,boolean restock) {}
    record PaymentRow(String method,BigDecimal amount) {}
    record SaleSummaryRow(long id,String documentNumber,long sellerId,String status,OffsetDateTime soldAt,BigDecimal total) {}
    record PurchaseSummaryRow(long id,String documentNumber,String status,OffsetDateTime purchasedAt,long supplierId,String supplierName,long itemCount,BigDecimal total) {}

    Header saleHeader(long id){return db.query("SELECT id,document_number,status,seller_id FROM sales WHERE id=?",(r,n)->new Header(r.getLong("id"),r.getString("document_number"),r.getString("status"),r.getObject("seller_id",Long.class)),id).stream().findFirst().orElseThrow(()->BusinessException.missing("Запись не найдена"));}
    Header purchaseHeader(long id){return basicHeader("purchases",id);}
    Header returnHeader(long id){return basicHeader("sale_returns",id);}
    private Header basicHeader(String table,long id){return db.query("SELECT id,document_number,status FROM "+table+" WHERE id=?",(r,n)->new Header(r.getLong("id"),r.getString("document_number"),r.getString("status"),null),id).stream().findFirst().orElseThrow(()->BusinessException.missing("Запись не найдена"));}

    List<SaleItemRow> saleItems(long id){return db.query("""
            SELECT i.id,i.product_id,i.product_name,i.product_sku,i.quantity,i.unit_price,
                i.discount_amount,i.line_total,COALESCE(x.returned_quantity,0) returned_quantity,
                COALESCE(x.returned_amount,0)::numeric(19,2) returned_amount
            FROM sale_items i LEFT JOIN (
                SELECT ri.sale_item_id,sum(ri.quantity) returned_quantity,sum(ri.refund_amount) returned_amount
                FROM return_items ri JOIN sale_returns r ON r.id=ri.return_id
                WHERE r.status='POSTED' GROUP BY ri.sale_item_id
            ) x ON x.sale_item_id=i.id WHERE i.sale_id=? ORDER BY i.id
            """,(r,n)->new SaleItemRow(r.getLong("id"),r.getLong("product_id"),r.getString("product_name"),
            r.getString("product_sku"),r.getInt("quantity"),r.getBigDecimal("unit_price"),
            r.getBigDecimal("discount_amount"),r.getBigDecimal("line_total"),r.getInt("returned_quantity"),
            r.getBigDecimal("returned_amount")),id);}
    List<PurchaseItemRow> purchaseItems(long id){return db.query("SELECT id,product_id,quantity,unit_cost,line_total FROM purchase_items WHERE purchase_id=? ORDER BY id",(r,n)->new PurchaseItemRow(r.getLong("id"),r.getLong("product_id"),r.getInt("quantity"),r.getBigDecimal("unit_cost"),r.getBigDecimal("line_total")),id);}
    List<ReturnItemRow> returnItems(long id){return db.query("SELECT id,sale_item_id,quantity,refund_amount,restock FROM return_items WHERE return_id=? ORDER BY id",(r,n)->new ReturnItemRow(r.getLong("id"),r.getLong("sale_item_id"),r.getInt("quantity"),r.getBigDecimal("refund_amount"),r.getBoolean("restock")),id);}
    List<PaymentRow> salePayments(long id){return payments("sale_id",id);}
    List<PaymentRow> returnPayments(long id){return payments("return_id",id);}
    private List<PaymentRow> payments(String column,long id){return db.query("SELECT method,amount FROM payments WHERE "+column+"=? ORDER BY id",(r,n)->new PaymentRow(r.getString("method"),r.getBigDecimal("amount")),id);}
    BigDecimal saleTotal(long id){return db.amount("SELECT COALESCE(sum(line_total),0) FROM sale_items WHERE sale_id=?",id);}
    BigDecimal purchaseTotal(long id){return db.amount("SELECT COALESCE(sum(line_total),0) FROM purchase_items WHERE purchase_id=?",id);}
    BigDecimal returnTotal(long id){return db.amount("SELECT COALESCE(sum(refund_amount),0) FROM return_items WHERE return_id=?",id);}
    BigDecimal saleBonus(long id){return db.amount("SELECT COALESCE(sum(amount),0) FROM bonus_entries WHERE sale_id=?",id);}
    BigDecimal returnBonus(long id){return db.amount("SELECT COALESCE(sum(amount),0) FROM bonus_entries WHERE return_id=?",id);}

    List<SaleSummaryRow> sales(boolean owner,Long employeeId,int limit,int offset){return db.query("SELECT s.id,s.document_number,s.seller_id,s.status,s.sold_at,(SELECT sum(line_total) FROM sale_items WHERE sale_id=s.id) total FROM sales s WHERE (? OR s.seller_id=?) AND s.status='POSTED' ORDER BY s.id DESC LIMIT ? OFFSET ?",(r,n)->new SaleSummaryRow(r.getLong("id"),r.getString("document_number"),r.getLong("seller_id"),r.getString("status"),r.getObject("sold_at",OffsetDateTime.class),r.getBigDecimal("total")),owner,employeeId,limit,offset);}
    List<PurchaseSummaryRow> purchases(int limit,int offset){return db.query("SELECT p.id,p.document_number,p.status,p.purchased_at,p.supplier_id,s.name supplier_name,count(i.id) item_count,COALESCE(sum(i.line_total),0)::numeric(19,2) total FROM purchases p JOIN suppliers s ON s.id=p.supplier_id LEFT JOIN purchase_items i ON i.purchase_id=p.id WHERE p.status='POSTED' GROUP BY p.id,s.name ORDER BY p.purchased_at DESC,p.id DESC LIMIT ? OFFSET ?",(r,n)->new PurchaseSummaryRow(r.getLong("id"),r.getString("document_number"),r.getString("status"),r.getObject("purchased_at",OffsetDateTime.class),r.getLong("supplier_id"),r.getString("supplier_name"),r.getLong("item_count"),r.getBigDecimal("total")),limit,offset);}
}
