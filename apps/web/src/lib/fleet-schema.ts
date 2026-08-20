import * as v from "valibot";

type ValidationMessages = {
    required: string;
    number: string;
    integer: string;
};

const requiredString = (message: string) =>
    v.pipe(v.string(), v.trim(), v.minLength(1, message));

const nonNegativeNumberString = (message: string) =>
    v.pipe(
        v.string(),
        v.minLength(1, message),
        v.transform(Number),
        v.number(message),
        v.minValue(0, message),
    );

const optionalNonNegativeNumberString = (message: string) =>
    v.union([v.literal(""), nonNegativeNumberString(message)]);

export const createVehicleFormSchema = (messages: ValidationMessages) =>
    v.object({
        brand: requiredString(messages.required),
        model: requiredString(messages.required),
        year: v.pipe(
            v.string(),
            v.minLength(1, messages.integer),
            v.transform(Number),
            v.number(messages.integer),
            v.integer(messages.integer),
            v.minValue(1800, messages.integer),
        ),
        licensePlate: requiredString(messages.required),
        fingerprint: v.string(),
        odometer: optionalNonNegativeNumberString(messages.number),
        fuelLevel: v.union([
            v.literal(""),
            v.pipe(
                nonNegativeNumberString(messages.number),
                v.maxValue(100, messages.number),
            ),
        ]),
        maintenanceEvery: nonNegativeNumberString(messages.number),
        assessmentMonth: v.pipe(
            v.string(),
            v.minLength(1, messages.integer),
            v.transform(Number),
            v.number(messages.integer),
            v.integer(messages.integer),
            v.minValue(1, messages.integer),
            v.maxValue(12, messages.integer),
        ),
        smartSupport: v.boolean(),
    });

export type VehicleFormValues = v.InferInput<
    ReturnType<typeof createVehicleFormSchema>
>;

export const createMaintenanceFormSchema = (numberMessage: string) =>
    v.object({
        odometer: optionalNonNegativeNumberString(numberMessage),
        mechanic: v.string(),
        note: v.string(),
    });

export type MaintenanceFormValues = v.InferInput<
    ReturnType<typeof createMaintenanceFormSchema>
>;
