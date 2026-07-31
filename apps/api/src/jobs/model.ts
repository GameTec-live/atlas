import { t } from "elysia";
import { dbModel } from "../db/model";

const jobInsertModel = t.Object({
    ...t.Omit(t.Object(dbModel.insert.job), ["id", "createdAt", "updatedAt"])
        .properties,
    assignedDriverId: t.Optional(t.String({ format: "uuid" })),
    to: t.Optional(
        t.Tuple([
            t.Number({ minimum: -90, maximum: 90 }),
            t.Number({ minimum: -180, maximum: 180 }),
        ]),
    ),
    from: t.Tuple([
        t.Number({ minimum: -90, maximum: 90 }),
        t.Number({ minimum: -180, maximum: 180 }),
    ]),
});

export const JobModel = {
    jobInsertModel: t.Omit(jobInsertModel, ["startedAt", "completedAt"]),
    jobUpdateModel: t.Partial(jobInsertModel),
    jobAssignModel: t.Optional(
        t.Pick(t.Partial(jobInsertModel), [
            "assignedDriverId",
            "dueDate",
            "to",
        ]),
    ),
} as const;
