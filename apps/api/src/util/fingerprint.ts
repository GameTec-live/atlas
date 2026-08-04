import { eq } from "drizzle-orm";
import { db } from "../db";
import { vehicle } from "../db/schema";

export const resolveFingerprint = async (fingerprint: string) => {
    const [result] = await db
        .select({
            vehicleId: vehicle.id,
        })
        .from(vehicle)
        .where(eq(vehicle.fingerprint, fingerprint));

    if (!result) {
        return null;
    }

    return result.vehicleId;
};
