import { t } from "elysia";

const key = t.String({ minLength: 1 });
const value = t.String({ minLength: 1 });

export const ShortnameModel = {
    params: t.Object({ key }),
    insert: t.Object({ key, value }),
    update: t.Object({ value }),
} as const;
