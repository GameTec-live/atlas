CREATE TYPE "role_enum" AS ENUM('driver', 'dispatcher');--> statement-breakpoint
CREATE TABLE "account" (
	"id" text PRIMARY KEY,
	"account_id" text NOT NULL,
	"provider_id" text NOT NULL,
	"user_id" text NOT NULL,
	"access_token" text,
	"refresh_token" text,
	"id_token" text,
	"access_token_expires_at" timestamp,
	"refresh_token_expires_at" timestamp,
	"scope" text,
	"password" text,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp NOT NULL
);
--> statement-breakpoint
CREATE TABLE "job" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"assigned_driver_id" text,
	"vehicle_id" uuid,
	"from" point NOT NULL,
	"to" point,
	"due_date" timestamp DEFAULT now() NOT NULL,
	"note" text,
	"started_at" timestamp,
	"completed_at" timestamp,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "logbook" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"vehicle_id" uuid NOT NULL,
	"driver_id" text,
	"start_odometer" bigint NOT NULL,
	"end_odometer" bigint,
	"started_at" timestamp DEFAULT now() NOT NULL,
	"ended_at" timestamp,
	"revenue" real,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "logbook_endOdometer_check" CHECK ("end_odometer" IS NULL OR "end_odometer" >= "start_odometer"),
	CONSTRAINT "logbook_revenue_check" CHECK ("revenue" IS NULL OR "revenue" >= 0),
	CONSTRAINT "logbook_endedAt_check" CHECK ("ended_at" IS NULL OR "ended_at" >= "started_at")
);
--> statement-breakpoint
CREATE TABLE "maintenance" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"vehicle_id" uuid NOT NULL,
	"note" text,
	"odometer" bigint,
	"mechanic" text,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "maintenance_odometer_check" CHECK ("odometer" >= 0)
);
--> statement-breakpoint
CREATE TABLE "role" (
	"driver_id" text,
	"role" "role_enum" NOT NULL,
	"date" date DEFAULT now(),
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "role_pkey" PRIMARY KEY("driver_id","date")
);
--> statement-breakpoint
CREATE TABLE "session" (
	"id" text PRIMARY KEY,
	"expires_at" timestamp NOT NULL,
	"token" text NOT NULL UNIQUE,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp NOT NULL,
	"ip_address" text,
	"user_agent" text,
	"user_id" text NOT NULL,
	"impersonated_by" text
);
--> statement-breakpoint
CREATE TABLE "shortname" (
	"key" text PRIMARY KEY,
	"value" text NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user" (
	"id" text PRIMARY KEY,
	"name" text NOT NULL,
	"email" text NOT NULL UNIQUE,
	"email_verified" boolean DEFAULT false NOT NULL,
	"image" text,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	"username" text UNIQUE,
	"display_username" text,
	"role" text,
	"banned" boolean DEFAULT false,
	"ban_reason" text,
	"ban_expires" timestamp
);
--> statement-breakpoint
CREATE TABLE "vehicle" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"brand" text NOT NULL,
	"model" text NOT NULL,
	"year" timestamp NOT NULL,
	"license_plate" text NOT NULL,
	"odometer" bigint,
	"fuel_level" real,
	"maintenance_every" integer NOT NULL,
	"assessment_month" timestamp NOT NULL,
	"smart_support" boolean DEFAULT true NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "vehicle_maintenanceEvery_check" CHECK ("maintenance_every" >= 0),
	CONSTRAINT "vehicle_fuelLevel_check" CHECK ("fuel_level" >= 0 AND "fuel_level" <= 100),
	CONSTRAINT "vehicle_odometer_check" CHECK ("odometer" >= 0)
);
--> statement-breakpoint
CREATE TABLE "verification" (
	"id" text PRIMARY KEY,
	"identifier" text NOT NULL,
	"value" text NOT NULL,
	"expires_at" timestamp NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE INDEX "account_userId_idx" ON "account" ("user_id");--> statement-breakpoint
CREATE INDEX "job_assignedDriverId_idx" ON "job" ("assigned_driver_id");--> statement-breakpoint
CREATE INDEX "job_dueDate_idx" ON "job" ("due_date");--> statement-breakpoint
CREATE INDEX "logbook_vehicleId_idx" ON "logbook" ("vehicle_id");--> statement-breakpoint
CREATE INDEX "logbook_driverId_idx" ON "logbook" ("driver_id");--> statement-breakpoint
CREATE INDEX "maintenance_vehicleId_idx" ON "maintenance" ("vehicle_id");--> statement-breakpoint
CREATE INDEX "maintenance_createdAt_idx" ON "maintenance" ("created_at");--> statement-breakpoint
CREATE INDEX "role_date_idx" ON "role" ("date");--> statement-breakpoint
CREATE INDEX "role_role_idx" ON "role" ("role");--> statement-breakpoint
CREATE INDEX "session_userId_idx" ON "session" ("user_id");--> statement-breakpoint
CREATE INDEX "vehicle_licensePlate_idx" ON "vehicle" ("license_plate");--> statement-breakpoint
CREATE INDEX "vehicle_brand_idx" ON "vehicle" ("brand");--> statement-breakpoint
CREATE INDEX "vehicle_model_idx" ON "vehicle" ("model");--> statement-breakpoint
CREATE INDEX "verification_identifier_idx" ON "verification" ("identifier");--> statement-breakpoint
ALTER TABLE "account" ADD CONSTRAINT "account_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "job" ADD CONSTRAINT "job_assigned_driver_id_user_id_fkey" FOREIGN KEY ("assigned_driver_id") REFERENCES "user"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "job" ADD CONSTRAINT "job_vehicle_id_vehicle_id_fkey" FOREIGN KEY ("vehicle_id") REFERENCES "vehicle"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "logbook" ADD CONSTRAINT "logbook_vehicle_id_vehicle_id_fkey" FOREIGN KEY ("vehicle_id") REFERENCES "vehicle"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "logbook" ADD CONSTRAINT "logbook_driver_id_user_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "user"("id") ON DELETE SET NULL;--> statement-breakpoint
ALTER TABLE "maintenance" ADD CONSTRAINT "maintenance_vehicle_id_vehicle_id_fkey" FOREIGN KEY ("vehicle_id") REFERENCES "vehicle"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "role" ADD CONSTRAINT "role_driver_id_user_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "user"("id") ON DELETE CASCADE;--> statement-breakpoint
ALTER TABLE "session" ADD CONSTRAINT "session_user_id_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user"("id") ON DELETE CASCADE;