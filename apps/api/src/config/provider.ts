import { resolve } from "node:path";
import { parse as parseToml, stringify as stringifyToml } from "smol-toml";
import * as v from "valibot";

type ConfigRecord = Record<string, unknown>;
type ConfigSchema = v.GenericSchema<ConfigRecord, ConfigRecord>;

type DeepReadonly<TValue> = TValue extends (...args: never[]) => unknown
    ? TValue
    : TValue extends readonly (infer TItem)[]
      ? readonly DeepReadonly<TItem>[]
      : TValue extends object
        ? { readonly [TKey in keyof TValue]: DeepReadonly<TValue[TKey]> }
        : TValue;

type ConfigInput<TSchema extends ConfigSchema> = v.InferInput<TSchema>;
type ConfigOutput<TSchema extends ConfigSchema> = v.InferOutput<TSchema>;
type ConfigKey<TSchema extends ConfigSchema> = Extract<
    keyof ConfigInput<TSchema>,
    string
>;

export interface CreateConfigOptions<TSchema extends ConfigSchema> {
    /**
     * A synchronous Valibot schema. Defaults in the schema are applied even
     * when the config file does not exist.
     */
    schema: TSchema;
    /**
     * The TOML file to load and write. Relative paths are resolved from the
     * current working directory.
     *
     * @default "./config.toml"
     */
    configFile?: string;
}

export interface ConfigSetOptions {
    /**
     * Persist the updated configuration immediately after it validates.
     *
     * @default false
     */
    write?: boolean;
}

export interface ConfigControls<TSchema extends ConfigSchema> {
    /** The absolute path of the backing TOML file. */
    readonly $path: string;

    /** Set one top-level configuration value after validating the full config. */
    $set<TKey extends ConfigKey<TSchema>>(
        key: TKey,
        value: ConfigInput<TSchema>[TKey],
        options?: ConfigSetOptions,
    ): Promise<void>;

    /** Set multiple top-level values after validating them as one update. */
    $set(
        values: Partial<ConfigInput<TSchema>>,
        options?: ConfigSetOptions,
    ): Promise<void>;

    /** Write the current, validated configuration to the TOML file. */
    $write(): Promise<void>;

    /** Reload and validate the TOML file, or schema defaults if it is absent. */
    $reload(): Promise<void>;

    /** Return the current deeply read-only configuration values. */
    $snapshot(): DeepReadonly<ConfigOutput<TSchema>>;
}

export type Config<TSchema extends ConfigSchema> = DeepReadonly<
    ConfigOutput<TSchema>
> &
    ConfigControls<TSchema>;

interface ConfigState<TOutput extends ConfigRecord> {
    input: ConfigRecord;
    output: TOutput;
}

const CONTROL_KEYS = new Set([
    "$path",
    "$set",
    "$write",
    "$reload",
    "$snapshot",
]);

const isRecord = (value: unknown): value is ConfigRecord =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const deepFreeze = <TValue>(value: TValue): DeepReadonly<TValue> => {
    if (typeof value !== "object" || value === null || Object.isFrozen(value)) {
        return value as DeepReadonly<TValue>;
    }

    for (const child of Object.values(value)) {
        deepFreeze(child);
    }

    return Object.freeze(value) as DeepReadonly<TValue>;
};

const readToml = async (configFile: string): Promise<ConfigRecord> => {
    const file = Bun.file(configFile);
    if (!(await file.exists())) return {};

    const parsed = parseToml(await file.text());
    if (!isRecord(parsed)) {
        throw new TypeError(
            `Config file must contain a TOML table: ${configFile}`,
        );
    }

    return parsed;
};

/**
 * Create a typed configuration object backed by a TOML file.
 *
 * Configuration values are available directly on the returned object. Direct
 * assignment is blocked; use `$set` so every update is validated.
 */
export const createConfig = async <const TSchema extends ConfigSchema>(
    options: CreateConfigOptions<TSchema>,
): Promise<Config<TSchema>> => {
    type Output = ConfigOutput<TSchema>;

    const configFile = resolve(options.configFile ?? "./config.toml");

    const validate = (input: unknown): DeepReadonly<Output> => {
        const output = v.parse(options.schema, input) as Output;

        for (const key of CONTROL_KEYS) {
            if (key in output) {
                throw new TypeError(
                    `Config schema key "${key}" is reserved for the config provider`,
                );
            }
        }

        return deepFreeze(output);
    };

    const createState = (input: ConfigRecord): ConfigState<Output> => ({
        input,
        output: validate(input) as Output,
    });

    let state = createState(await readToml(configFile));

    const persist = async (output: Output): Promise<ConfigState<Output>> => {
        let serialized = stringifyToml(output);
        if (!serialized.endsWith("\n")) serialized += "\n";

        // Validate the serialized form before touching the existing file. This
        // also rejects schema transforms whose output cannot be loaded again.
        const persistedInput = parseToml(serialized);
        if (!isRecord(persistedInput)) {
            throw new TypeError(
                "Serialized configuration must be a TOML table",
            );
        }
        const nextState = createState(persistedInput);

        await Bun.write(configFile, serialized);
        return nextState;
    };

    const write = async () => {
        state = await persist(state.output);
    };

    async function set(
        keyOrValues: ConfigKey<TSchema> | Partial<ConfigInput<TSchema>>,
        valueOrOptions?:
            | ConfigInput<TSchema>[ConfigKey<TSchema>]
            | ConfigSetOptions,
        maybeOptions?: ConfigSetOptions,
    ) {
        const isKeyUpdate = typeof keyOrValues === "string";
        const values = isKeyUpdate
            ? { [keyOrValues]: valueOrOptions }
            : keyOrValues;
        const setOptions = isKeyUpdate
            ? maybeOptions
            : (valueOrOptions as ConfigSetOptions | undefined);
        const nextState = createState({ ...state.input, ...values });

        state = setOptions?.write ? await persist(nextState.output) : nextState;
    }

    const controls: ConfigControls<TSchema> = {
        $path: configFile,
        $set: set as ConfigControls<TSchema>["$set"],
        $write: write,
        $reload: async () => {
            state = createState(await readToml(configFile));
        },
        $snapshot: () => state.output as DeepReadonly<Output>,
    };

    const target = {};
    for (const [key, value] of Object.entries(controls)) {
        Object.defineProperty(target, key, {
            configurable: true,
            enumerable: false,
            writable: false,
            value,
        });
    }

    return new Proxy(target, {
        get(targetObject, property, receiver) {
            if (
                typeof property === "string" &&
                Object.hasOwn(state.output, property)
            ) {
                return state.output[property];
            }
            return Reflect.get(targetObject, property, receiver);
        },
        getOwnPropertyDescriptor(targetObject, property) {
            if (
                typeof property === "string" &&
                Object.hasOwn(state.output, property)
            ) {
                return {
                    configurable: true,
                    enumerable: true,
                    writable: false,
                    value: state.output[property],
                };
            }
            return Reflect.getOwnPropertyDescriptor(targetObject, property);
        },
        has(targetObject, property) {
            return (
                (typeof property === "string" &&
                    Object.hasOwn(state.output, property)) ||
                Reflect.has(targetObject, property)
            );
        },
        ownKeys(targetObject) {
            return [
                ...new Set([
                    ...Reflect.ownKeys(state.output),
                    ...Reflect.ownKeys(targetObject),
                ]),
            ];
        },
        set() {
            throw new TypeError(
                "Config is read-only; update values with config.$set(...)",
            );
        },
        deleteProperty() {
            throw new TypeError(
                "Config is read-only; update values with config.$set(...)",
            );
        },
        defineProperty() {
            throw new TypeError(
                "Config is read-only; update values with config.$set(...)",
            );
        },
    }) as Config<TSchema>;
};
