import { defineRelationsPart } from "drizzle-orm";
import {
    bigint,
    boolean,
    date,
    index,
    integer,
    pgEnum,
    pgTable,
    point,
    primaryKey,
    real,
    text,
    timestamp,
    uuid,
} from "drizzle-orm/pg-core";

export const user = pgTable("user", {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    email: text("email").notNull().unique(),
    emailVerified: boolean("email_verified").default(false).notNull(),
    image: text("image"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
        .defaultNow()
        .$onUpdate(() => /* @__PURE__ */ new Date())
        .notNull(),
});

export const session = pgTable(
    "session",
    {
        id: text("id").primaryKey(),
        expiresAt: timestamp("expires_at").notNull(),
        token: text("token").notNull().unique(),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
        ipAddress: text("ip_address"),
        userAgent: text("user_agent"),
        userId: text("user_id")
            .notNull()
            .references(() => user.id, { onDelete: "cascade" }),
    },
    (table) => [index("session_userId_idx").on(table.userId)],
);

export const account = pgTable(
    "account",
    {
        id: text("id").primaryKey(),
        accountId: text("account_id").notNull(),
        providerId: text("provider_id").notNull(),
        userId: text("user_id")
            .notNull()
            .references(() => user.id, { onDelete: "cascade" }),
        accessToken: text("access_token"),
        refreshToken: text("refresh_token"),
        idToken: text("id_token"),
        accessTokenExpiresAt: timestamp("access_token_expires_at"),
        refreshTokenExpiresAt: timestamp("refresh_token_expires_at"),
        scope: text("scope"),
        password: text("password"),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [index("account_userId_idx").on(table.userId)],
);

export const verification = pgTable(
    "verification",
    {
        id: text("id").primaryKey(),
        identifier: text("identifier").notNull(),
        value: text("value").notNull(),
        expiresAt: timestamp("expires_at").notNull(),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [index("verification_identifier_idx").on(table.identifier)],
);

export const vehicle = pgTable(
    "vehicle",
    {
        id: uuid("id").primaryKey().defaultRandom(),
        brand: text("brand").notNull(),
        model: text("model").notNull(),
        year: date("year").notNull(),
        licensePlate: text("license_plate").notNull(),
        odometer: bigint("odometer", { mode: "number" }),
        fuelLevel: real("fuel_level"),
        maintenanceEvery: integer("maintenance_every").notNull(),
        assessmentMonth: date("assessment_month").notNull(),
        smartSupport: boolean("smart_support").default(true).notNull(),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [
        index("vehicle_licensePlate_idx").on(table.licensePlate),
        index("vehicle_brand_idx").on(table.brand),
        index("vehicle_model_idx").on(table.model),
    ],
);

export const maintenance = pgTable(
    "maintenance",
    {
        id: uuid("id").primaryKey().defaultRandom(),
        vehicleId: uuid("vehicle_id")
            .notNull()
            .references(() => vehicle.id, { onDelete: "cascade" }),
        note: text("note").notNull(),
        mechanic: text("mechanic"),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [index("maintenance_vehicleId_idx").on(table.vehicleId)],
);

export const job = pgTable(
    "job",
    {
        id: uuid("id").primaryKey().defaultRandom(),
        assignedDriverId: text("assigned_driver_id").references(() => user.id, {
            onDelete: "set null",
        }),
        vehicleId: uuid("vehicle_id").references(() => vehicle.id, {
            onDelete: "set null",
        }),
        from: point("from").notNull(),
        to: point("to"),
        dueDate: timestamp("due_date").defaultNow().notNull(),
        note: text("note"),
        startedAt: timestamp("started_at"),
        completedAt: timestamp("completed_at"),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [
        index("job_assignedDriverId_idx").on(table.assignedDriverId),
        index("job_dueDate_idx").on(table.dueDate),
    ],
);

export const logbook = pgTable(
    "logbook",
    {
        id: uuid("id").primaryKey().defaultRandom(),
        vehicleId: uuid("vehicle_id")
            .notNull()
            .references(() => vehicle.id, { onDelete: "cascade" }),
        driverId: text("driver_id").references(() => user.id, {
            onDelete: "set null",
        }),
        startOdometer: bigint("start_odometer", { mode: "number" }).notNull(),
        endOdometer: bigint("end_odometer", { mode: "number" }),
        startedAt: timestamp("started_at").defaultNow().notNull(),
        endedAt: timestamp("ended_at"),
        revenue: real("revenue"),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [
        index("logbook_vehicleId_idx").on(table.vehicleId),
        index("logbook_driverId_idx").on(table.driverId),
    ],
);

export const roleEnum = pgEnum("role_enum", ["driver", "dispatcher"]);

export const role = pgTable(
    "role",
    {
        driverId: text("driver_id")
            .notNull()
            .references(() => user.id, { onDelete: "cascade" }),
        role: roleEnum("role").notNull(),
        date: date("date").notNull().defaultNow(),
        createdAt: timestamp("created_at").defaultNow().notNull(),
        updatedAt: timestamp("updated_at")
            .defaultNow()
            .$onUpdate(() => /* @__PURE__ */ new Date())
            .notNull(),
    },
    (table) => [
        primaryKey({ columns: [table.driverId, table.date] }),
        index("role_date_idx").on(table.date),
        index("role_role_idx").on(table.role),
    ],
);

export const shortname = pgTable("shortname", {
    key: text("key").primaryKey(),
    value: text("value").notNull(),
});

export const relations = defineRelationsPart(
    {
        user,
        session,
        account,
        verification,
        vehicle,
        maintenance,
        job,
        logbook,
        role,
    },
    (r) => ({
        user: {
            sessions: r.many.session({
                from: r.user.id,
                to: r.session.userId,
            }),
            accounts: r.many.account({
                from: r.user.id,
                to: r.account.userId,
            }),
            roles: r.many.role({
                from: r.user.id,
                to: r.role.driverId,
            }),
            logbooks: r.many.logbook({
                from: r.user.id,
                to: r.logbook.driverId,
            }),
            jobs: r.many.job({
                from: r.user.id,
                to: r.job.assignedDriverId,
            }),
        },
        session: {
            user: r.one.user({
                from: r.session.userId,
                to: r.user.id,
            }),
        },
        account: {
            user: r.one.user({
                from: r.account.userId,
                to: r.user.id,
            }),
        },
        vehicle: {
            maintenances: r.many.maintenance({
                from: r.vehicle.id,
                to: r.maintenance.vehicleId,
            }),
            jobs: r.many.job({
                from: r.vehicle.id,
                to: r.job.vehicleId,
            }),
            logbooks: r.many.logbook({
                from: r.vehicle.id,
                to: r.logbook.vehicleId,
            }),
        },
        maintenance: {
            vehicle: r.one.vehicle({
                from: r.maintenance.vehicleId,
                to: r.vehicle.id,
            }),
        },
        job: {
            vehicle: r.one.vehicle({
                from: r.job.vehicleId,
                to: r.vehicle.id,
            }),
            assignedDriver: r.one.user({
                from: r.job.assignedDriverId,
                to: r.user.id,
            }),
        },
        logbook: {
            vehicle: r.one.vehicle({
                from: r.logbook.vehicleId,
                to: r.vehicle.id,
            }),
            driver: r.one.user({
                from: r.logbook.driverId,
                to: r.user.id,
            }),
        },
        role: {
            driver: r.one.user({
                from: r.role.driverId,
                to: r.user.id,
            }),
        },
    }),
);
