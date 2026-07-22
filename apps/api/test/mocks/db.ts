import { mock } from "bun:test";
import { drizzle } from "drizzle-orm/node-postgres";
import {
    Client,
    type QueryArrayConfig,
    type QueryArrayResult,
    type QueryConfig,
    type QueryConfigValues,
    type QueryResult,
    type QueryResultRow,
    type Submittable,
} from "pg";
import {
    type account as accountTable,
    type job as jobTable,
    type logbook as logbookTable,
    type maintenance as maintenanceTable,
    relations,
    type role as roleTable,
    type session as sessionTable,
    type shortname as shortnameTable,
    type user as userTable,
    type vehicle as vehicleTable,
    type verification as verificationTable,
} from "@/src/db/schema";

type DbOperation =
    | "select"
    | "insert"
    | "update"
    | "delete"
    | "count"
    | "execute";

const createdAt = new Date("2026-01-01T08:00:00.000Z");
const updatedAt = new Date("2026-01-02T08:00:00.000Z");
const userId = "user-1";
const vehicleId = "7bb0de4d-bcdd-4c99-a852-a17a4bbdb3de";

export const exampleData = {
    user: [
        {
            id: userId,
            name: "Test Driver",
            email: "driver@example.com",
            emailVerified: true,
            image: null,
            createdAt,
            updatedAt,
            username: "test-driver",
            displayUsername: "Test Driver",
            role: "driver",
            banned: false,
            banReason: null,
            banExpires: null,
        },
    ] satisfies (typeof userTable.$inferSelect)[],
    session: [
        {
            id: "session-1",
            expiresAt: new Date("2099-01-01T00:00:00.000Z"),
            token: "test-token",
            createdAt,
            updatedAt,
            ipAddress: "127.0.0.1",
            userAgent: "Bun test",
            userId,
            impersonatedBy: null,
        },
    ] satisfies (typeof sessionTable.$inferSelect)[],
    account: [
        {
            id: "account-1",
            accountId: "driver@example.com",
            providerId: "credential",
            userId,
            accessToken: null,
            refreshToken: null,
            idToken: null,
            accessTokenExpiresAt: null,
            refreshTokenExpiresAt: null,
            scope: null,
            password: "hashed-test-password",
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof accountTable.$inferSelect)[],
    verification: [
        {
            id: "verification-1",
            identifier: "driver@example.com",
            value: "test-verification-token",
            expiresAt: new Date("2099-01-01T00:00:00.000Z"),
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof verificationTable.$inferSelect)[],
    vehicle: [
        {
            id: vehicleId,
            brand: "Volkswagen",
            model: "Transporter",
            year: new Date("2024-01-01T00:00:00.000Z"),
            licensePlate: "ATLAS-1",
            odometer: 12_500,
            fuelLevel: 75,
            maintenanceEvery: 15_000,
            assessmentMonth: new Date("2026-07-01T00:00:00.000Z"),
            smartSupport: true,
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof vehicleTable.$inferSelect)[],
    maintenance: [
        {
            id: "d6503952-72f5-4b73-a826-e1ab44e0ba72",
            vehicleId,
            note: "Replace engine oil and filter",
            odometer: 12_000,
            mechanic: "Atlas Workshop",
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof maintenanceTable.$inferSelect)[],
    job: [
        {
            id: "b0f17458-94c5-47c9-ab4b-68aadf088f4c",
            assignedDriverId: userId,
            vehicleId,
            from: [16.3738, 48.2082],
            to: [16.3122, 48.1947],
            dueDate: new Date("2026-07-20T12:00:00.000Z"),
            note: "Deliver package to destination",
            startedAt: null,
            completedAt: null,
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof jobTable.$inferSelect)[],
    logbook: [
        {
            id: "62a9d899-ac1c-49d1-8d84-ee2280ee6d82",
            vehicleId,
            driverId: userId,
            startOdometer: 12_500,
            endOdometer: 12_548,
            startedAt: new Date("2026-07-20T08:00:00.000Z"),
            endedAt: new Date("2026-07-20T09:15:00.000Z"),
            revenue: 84.5,
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof logbookTable.$inferSelect)[],
    role: [
        {
            driverId: userId,
            role: "driver",
            date: new Date("2026-07-20T00:00:00.000Z"),
            createdAt,
            updatedAt,
        },
    ] satisfies (typeof roleTable.$inferSelect)[],
    shortname: [
        {
            key: "primary-depot",
            value: "Vienna Central Depot",
        },
    ] satisfies (typeof shortnameTable.$inferSelect)[],
};

const tableNames = [
    "user",
    "session",
    "account",
    "verification",
    "vehicle",
    "maintenance",
    "job",
    "logbook",
    "role",
    "shortname",
] as const;

export type TableName = (typeof tableNames)[number];

const toTimestamp = (value: Date | null) =>
    value ? value.toISOString().slice(0, -1) : null;

const defaultTableRows: Record<TableName, unknown[][]> = {
    user: exampleData.user.map((row) => [
        row.id,
        row.name,
        row.email,
        row.emailVerified,
        row.image,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
        row.username,
        row.displayUsername,
        row.role,
        row.banned,
        row.banReason,
        toTimestamp(row.banExpires),
    ]),
    session: exampleData.session.map((row) => [
        row.id,
        toTimestamp(row.expiresAt),
        row.token,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
        row.ipAddress,
        row.userAgent,
        row.userId,
        row.impersonatedBy,
    ]),
    account: exampleData.account.map((row) => [
        row.id,
        row.accountId,
        row.providerId,
        row.userId,
        row.accessToken,
        row.refreshToken,
        row.idToken,
        toTimestamp(row.accessTokenExpiresAt),
        toTimestamp(row.refreshTokenExpiresAt),
        row.scope,
        row.password,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    verification: exampleData.verification.map((row) => [
        row.id,
        row.identifier,
        row.value,
        toTimestamp(row.expiresAt),
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    vehicle: exampleData.vehicle.map((row) => [
        row.id,
        row.brand,
        row.model,
        toTimestamp(row.year),
        row.licensePlate,
        row.odometer,
        row.fuelLevel,
        row.maintenanceEvery,
        toTimestamp(row.assessmentMonth),
        row.smartSupport,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    maintenance: exampleData.maintenance.map((row) => [
        row.id,
        row.vehicleId,
        row.note,
        row.odometer,
        row.mechanic,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    job: exampleData.job.map((row) => [
        row.id,
        row.assignedDriverId,
        row.vehicleId,
        `(${row.from[0]},${row.from[1]})`,
        row.to ? `(${row.to[0]},${row.to[1]})` : null,
        toTimestamp(row.dueDate),
        row.note,
        toTimestamp(row.startedAt),
        toTimestamp(row.completedAt),
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    logbook: exampleData.logbook.map((row) => [
        row.id,
        row.vehicleId,
        row.driverId,
        row.startOdometer,
        row.endOdometer,
        toTimestamp(row.startedAt),
        toTimestamp(row.endedAt),
        row.revenue,
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    role: exampleData.role.map((row) => [
        row.driverId,
        row.role,
        toTimestamp(row.date),
        toTimestamp(row.createdAt),
        toTimestamp(row.updatedAt),
    ]),
    shortname: exampleData.shortname.map((row) => [row.key, row.value]),
};

const tableRows = { ...defaultTableRows };

const defaultDbRows: Record<DbOperation, unknown[][] | undefined> = {
    select: undefined,
    insert: [],
    update: [],
    delete: [],
    count: undefined,
    execute: [],
};

const dbRows = { ...defaultDbRows };
const dbRowCounts: Partial<Record<DbOperation, number>> = {};

const getSql = (query: unknown) => {
    if (typeof query === "string") return query;
    if (
        typeof query === "object" &&
        query !== null &&
        "text" in query &&
        typeof query.text === "string"
    ) {
        return query.text;
    }
    return "";
};

const getDbOperation = (query: unknown): DbOperation => {
    const sql = getSql(query).trim().toLowerCase();

    if (sql.startsWith("select count(")) return "count";
    if (sql.startsWith("select")) return "select";
    if (sql.startsWith("insert")) return "insert";
    if (sql.startsWith("update")) return "update";
    if (sql.startsWith("delete")) return "delete";
    return "execute";
};

const getTableName = (query: unknown): TableName | undefined => {
    const sql = getSql(query).trim().toLowerCase();

    return tableNames.find((table) =>
        new RegExp(`\\b(?:from|into|update)\\s+"${table}"(?:\\s|$)`).test(sql),
    );
};

const queryDatabase = async (query: unknown, _values?: unknown) => {
    const operation = getDbOperation(query);
    const table = getTableName(query);
    const rows =
        dbRows[operation] ??
        (operation === "count"
            ? [[table ? tableRows[table].length : 0]]
            : operation === "select" && table
              ? tableRows[table]
              : []);

    return {
        command: operation.toUpperCase(),
        rowCount: dbRowCounts[operation] ?? rows.length,
        oid: 0,
        fields: [],
        rows,
    };
};

export const dbClientQueryMock = mock(queryDatabase);

class TestPgClient extends Client {
    override query<T extends Submittable>(queryStream: T): T;
    override query<R extends unknown[] = unknown[], I extends unknown[] = []>(
        queryConfig: QueryArrayConfig<I>,
        values?: QueryConfigValues<I>,
    ): Promise<QueryArrayResult<R>>;
    override query<
        R extends QueryResultRow = QueryResultRow,
        I extends unknown[] = [],
    >(queryConfig: QueryConfig<I>): Promise<QueryResult<R>>;
    override query<
        R extends QueryResultRow = QueryResultRow,
        I extends unknown[] = [],
    >(
        queryTextOrConfig: string | QueryConfig<I>,
        values?: QueryConfigValues<I>,
    ): Promise<QueryResult<R>>;
    override query<R extends unknown[] = unknown[], I extends unknown[] = []>(
        queryConfig: QueryArrayConfig<I>,
        callback: (error: Error, result: QueryArrayResult<R>) => void,
    ): void;
    override query<
        R extends QueryResultRow = QueryResultRow,
        I extends unknown[] = [],
    >(
        queryTextOrConfig: string | QueryConfig<I>,
        callback: (error: Error, result: QueryResult<R>) => void,
    ): void;
    override query<
        R extends QueryResultRow = QueryResultRow,
        I extends unknown[] = [],
    >(
        queryText: string,
        values: QueryConfigValues<I>,
        callback: (error: Error, result: QueryResult<R>) => void,
    ): void;
    override query(
        query: unknown,
        valuesOrCallback?: unknown,
        callback?: unknown,
    ): unknown {
        if (typeof query === "object" && query !== null && "submit" in query) {
            return query;
        }

        const result = dbClientQueryMock(
            query,
            typeof valuesOrCallback === "function"
                ? undefined
                : valuesOrCallback,
        );
        const resultCallback =
            typeof callback === "function"
                ? callback
                : typeof valuesOrCallback === "function"
                  ? valuesOrCallback
                  : undefined;

        if (resultCallback) {
            result.then((value) => resultCallback(undefined, value));
            return;
        }

        return result;
    }
}

export const pgClientMock = new TestPgClient();

export const dbMock = drizzle({
    client: pgClientMock,
    relations,
});

export const setDbMockRows = (operation: DbOperation, rows: unknown[][]) => {
    dbRows[operation] = rows;
};

export const setDbMockTableRows = (table: TableName, rows: unknown[][]) => {
    tableRows[table] = rows;
};

export const getDbMockTableRows = (table: TableName) =>
    tableRows[table].map((row) => [...row]);

export const setDbMockRowCount = (operation: DbOperation, rowCount: number) => {
    dbRowCounts[operation] = rowCount;
};

export const resetDbMocks = () => {
    Object.assign(dbRows, defaultDbRows);
    Object.assign(tableRows, defaultTableRows);
    for (const operation of Object.keys(dbRowCounts) as DbOperation[]) {
        delete dbRowCounts[operation];
    }
    dbClientQueryMock.mockReset();
    dbClientQueryMock.mockImplementation(queryDatabase);
};

mock.module("@/src/db", () => ({
    db: dbMock,
}));
