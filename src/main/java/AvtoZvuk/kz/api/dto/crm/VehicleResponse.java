package AvtoZvuk.kz.api.dto.crm;

public record VehicleResponse(long id, String make, String model, Integer productionYear,
                              String plateNumber, String notes) {}
