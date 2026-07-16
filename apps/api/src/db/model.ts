import { isTable, type Table } from "drizzle-orm";
import {
    type BuildSchema,
    createSchemaFactory,
} from "drizzle-orm/typebox-legacy";
import { t } from "elysia";
import * as schema from "./schema";

type TablesOnly<T> = {
    [K in keyof T as T[K] extends Table ? K : never]: Extract<T[K], Table>;
};

type InsertProperties<T extends Table> = BuildSchema<
    "insert",
    T["_"]["columns"],
    undefined
>["properties"];

type SelectProperties<T extends Table> = BuildSchema<
    "select",
    T["_"]["columns"],
    undefined
>["properties"];

type DbModel<T extends Record<string, Table>> = {
    insert: {
        [K in keyof T]: InsertProperties<T[K]>;
    };
    select: {
        [K in keyof T]: SelectProperties<T[K]>;
    };
};

const collectTables = <T extends Record<string, unknown>>(
    exports: T,
): TablesOnly<T> =>
    Object.fromEntries(
        Object.entries(exports).filter(([, value]) => isTable(value)),
    ) as TablesOnly<T>;

const { createInsertSchema, createSelectSchema } = createSchemaFactory({
    typeboxInstance: t,
});

const createDbModel = <T extends Record<string, Table>>(
    tables: T,
): DbModel<T> => {
    const insert = Object.fromEntries(
        Object.entries(tables).map(([name, table]) => [
            name,
            createInsertSchema(table).properties,
        ]),
    ) as DbModel<T>["insert"];

    const select = Object.fromEntries(
        Object.entries(tables).map(([name, table]) => [
            name,
            createSelectSchema(table).properties,
        ]),
    ) as DbModel<T>["select"];

    return { insert, select };
};

export const tables = collectTables(schema);
export const dbModel = createDbModel(tables);
