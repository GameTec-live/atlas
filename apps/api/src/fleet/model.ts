import { t } from "elysia";
import { dbModel } from "../db/model";

const vehicleInsertModel = t.Object({
    ...t.Omit(t.Object(dbModel.insert.vehicle), [
        "id",
        "createdAt",
        "updatedAt",
    ]).properties,
    odometer: t.Optional(t.Number({ minimum: 0 })),
    fuelLevel: t.Optional(t.Number({ minimum: 0, maximum: 100 })),
    maintenanceEvery: t.Number({ minimum: 0 }),
    year: t.Date(),
    assessmentMonth: t.Date(),
});

export const FleetModel = {
    vehicleInsertModel,
    vehicleUpdateModel: t.Partial(vehicleInsertModel),
} as const;
