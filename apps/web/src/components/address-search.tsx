import { useQuery } from "@tanstack/react-query";
import type { FocusEventHandler } from "react";
import { useMemo, useState } from "react";
import {
    Combobox,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
} from "@/components/ui/combobox";
import { Spinner } from "@/components/ui/spinner";
import { useDebounce } from "@/hooks/use-debounce";
import { m } from "@/paraglide/messages";
import { addressResolveQueryOptions } from "@/queries/geoservices";

type AddressOption = {
    value: string;
    label: string;
    context?: string;
    coordinates?: [number, number];
};

type AddressSearchProps = {
    value: string;
    onValueChange: (value: string) => void;
    onAddressSelect: (address: string, coordinates: [number, number]) => void;
    disabled?: boolean;
    placeholder?: string;
    "aria-label"?: string;
    className?: string;
    onFocus?: FocusEventHandler<HTMLInputElement>;
};

/** A server-backed address combobox that only commits selected search results. */
export function AddressSearch({
    value,
    onValueChange,
    onAddressSelect,
    disabled = false,
    placeholder,
    className,
    onFocus,
    "aria-label": ariaLabel,
}: AddressSearchProps) {
    const [isSearching, setIsSearching] = useState(false);
    const pendingSearchTerm = isSearching ? value.trim() : "";
    const debouncedSearchTerm = useDebounce(pendingSearchTerm, 300);
    const searchTerm = pendingSearchTerm.length >= 3 ? debouncedSearchTerm : "";
    const { data, isError, isFetching } = useQuery(
        addressResolveQueryOptions(searchTerm),
    );
    const isDebouncing =
        pendingSearchTerm.length >= 3 && searchTerm !== pendingSearchTerm;

    const options = useMemo(() => {
        if (!data || !("results" in data)) return [];

        const uniqueResults = new Map<string, AddressOption>();
        for (const result of data.results) {
            if (uniqueResults.has(result.display_name)) continue;

            uniqueResults.set(result.display_name, {
                value: result.display_name,
                label: result.display_name,
                context: [
                    result.kind,
                    result.locality,
                    result.country ? result.country : result.country_code,
                ]
                    .filter(Boolean)
                    .join(" · "),
                coordinates: [result.lat, result.lon],
            });
        }

        return [...uniqueResults.values()];
    }, [data]);
    const selectedOption = useMemo<AddressOption | null>(
        () => (value ? { value, label: value } : null),
        [value],
    );

    const emptyMessage = (() => {
        if (!isSearching || value.trim().length < 3) {
            return m.address_search_prompt();
        }
        if (isDebouncing || isFetching) return m.address_searching();
        if (isError || (data && "error" in data)) {
            return m.address_search_error();
        }
        return m.address_search_empty();
    })();

    return (
        <Combobox
            items={options}
            filter={null}
            autoHighlight
            inputValue={value}
            value={selectedOption}
            itemToStringLabel={(option: AddressOption) => option.label}
            itemToStringValue={(option: AddressOption) => option.value}
            isItemEqualToValue={(option, selected) =>
                option.value === selected.value
            }
            onInputValueChange={(nextValue, details) => {
                if (details.reason === "item-press") return;
                setIsSearching(true);
                onValueChange(nextValue);
            }}
            onValueChange={(option) => {
                if (!option?.coordinates) return;
                setIsSearching(false);
                onAddressSelect(option.value, option.coordinates);
            }}
        >
            <ComboboxInput
                aria-label={ariaLabel}
                disabled={disabled}
                onFocus={onFocus}
                placeholder={placeholder}
                showTrigger={false}
                className={className}
            />
            <ComboboxContent>
                <ComboboxList>
                    {options.map((option) => (
                        <ComboboxItem key={option.value} value={option}>
                            <span className="min-w-0">
                                <span className="block truncate">
                                    {option.label}
                                </span>
                                {option.context && (
                                    <span className="block truncate text-xs text-muted-foreground">
                                        {option.context}
                                    </span>
                                )}
                            </span>
                        </ComboboxItem>
                    ))}
                    <ComboboxEmpty>
                        <span className="flex items-center justify-center gap-2">
                            {isFetching && <Spinner />}
                            {emptyMessage}
                        </span>
                    </ComboboxEmpty>
                </ComboboxList>
            </ComboboxContent>
        </Combobox>
    );
}
