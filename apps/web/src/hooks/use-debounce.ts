import { useEffect, useState } from "react";

/** Returns a value after it has remained unchanged for the given delay. */
export function useDebounce<T>(value: T, delay: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timeout = window.setTimeout(
            () => setDebouncedValue(value),
            delay,
        );

        return () => window.clearTimeout(timeout);
    }, [delay, value]);

    return debouncedValue;
}
