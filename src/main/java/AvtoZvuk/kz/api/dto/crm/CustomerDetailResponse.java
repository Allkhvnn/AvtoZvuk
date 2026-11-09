package AvtoZvuk.kz.api.dto.crm;

import java.util.List;

public record CustomerDetailResponse(long id, String fullName, String phone, String notes,
                                     boolean active, List<VehicleResponse> vehicles,
                                     List<CustomerSaleResponse> sales) {}
