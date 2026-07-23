import { t } from "elysia";
import { dbModel } from "../db/model";

export const RoleModel = {
    roleClaimBody: t.Object({
        ...t.Omit(t.Object(dbModel.insert.role), [
            "id",
            "createdAt",
            "updatedAt",
        ]).properties,
    }),
} as const;
