ALTER TABLE "vehicle" ADD COLUMN "fingerprint" text;--> statement-breakpoint
ALTER TABLE "vehicle" ADD CONSTRAINT "vehicle_fingerprint_key" UNIQUE("fingerprint");--> statement-breakpoint
CREATE INDEX "vehicle_fingerprint_idx" ON "vehicle" ("fingerprint");