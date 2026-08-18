import { useCallback, useEffect, useRef } from "react";

/** Debounces a callback while always invoking its latest render. */
export function useDebouncedCallback<TArguments extends unknown[]>(
    callback: (...arguments_: TArguments) => void,
    delay: number,
) {
    const callbackRef = useRef(callback);
    const timeoutRef = useRef<number | undefined>(undefined);
    callbackRef.current = callback;

    const cancel = useCallback(() => {
        if (timeoutRef.current === undefined) return;
        window.clearTimeout(timeoutRef.current);
        timeoutRef.current = undefined;
    }, []);

    const debounce = useCallback(
        (...arguments_: TArguments) => {
            cancel();
            timeoutRef.current = window.setTimeout(() => {
                timeoutRef.current = undefined;
                callbackRef.current(...arguments_);
            }, delay);
        },
        [cancel, delay],
    );

    useEffect(() => cancel, [cancel]);

    return { debounce, cancel };
}
