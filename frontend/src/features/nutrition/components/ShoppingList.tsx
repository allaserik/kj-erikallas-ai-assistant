import { useState, useEffect } from "react";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { generateShoppingListFromMeals, generateShoppingListFromDateRange, type ShoppingListResponse, type ShoppingListItem } from "../../../shared/api/nutrition";
import { Button } from "../../../shared/ui/Button";
import { Card, CardBody, CardTitle, CardSubtitle } from "../../../shared/ui/Card";
import { Alert } from "../../../shared/ui/Alert";
import { ApiError } from "../../../shared/api/client";
import { Spinner } from "../../../shared/ui/Spinner";

interface ShoppingListProps {
    mealIds?: string[];
    dateRange?: { startDate: string; endDate: string };
}

export function ShoppingList({ mealIds, dateRange }: ShoppingListProps) {
    const getToken = useAuthToken();
    const [list, setList] = useState<ShoppingListResponse | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editingQuantities, setEditingQuantities] = useState<Map<string, number>>(new Map());

    useEffect(() => {
        loadShoppingList();
    }, [mealIds, dateRange]);

    const loadShoppingList = async () => {
        setIsLoading(true);
        setError(null);

        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");

            let result;
            if (mealIds && mealIds.length > 0) {
                result = await generateShoppingListFromMeals(mealIds, token);
            } else if (dateRange) {
                result = await generateShoppingListFromDateRange(
                    dateRange.startDate,
                    dateRange.endDate,
                    token
                );
            } else {
                setError("No meals or date range provided");
                return;
            }

            if (!result) {
                setError("Failed to generate shopping list");
                return;
            }

            setList(result);
            setEditingQuantities(new Map());
        } catch (err) {
            const message = err instanceof ApiError ? err.message : "Failed to generate shopping list";
            setError(message);
        } finally {
            setIsLoading(false);
        }
    };

    const handleQuantityChange = (itemId: string, newQuantity: number) => {
        const newQuantities = new Map(editingQuantities);
        newQuantities.set(itemId, newQuantity);
        setEditingQuantities(newQuantities);
    };

    const handleExcludeItem = (itemId: string) => {
        if (!list) return;

        const updatedItems = list.allItems.map(item =>
            item.id === itemId ? { ...item, excluded: !item.excluded } : item
        );

        const updatedList: ShoppingListResponse = {
            ...list,
            allItems: updatedItems,
            categorized: Object.fromEntries(
                Object.entries(list.categorized).map(([category, items]) => [
                    category,
                    items.map(item =>
                        item.id === itemId ? { ...item, excluded: !item.excluded } : item
                    )
                ])
            ),
            itemCount: updatedItems.filter(item => !item.excluded).length
        };

        setList(updatedList);
    };

    const handlePrint = () => {
        window.print();
    };

    const getDisplayQuantity = (item: ShoppingListItem): number => {
        return editingQuantities.get(item.id) ?? item.quantity;
    };

    if (error && !list) {
        return <Alert tone="error" title="Error" message={error} />;
    }

    return (
        <Card>
            <CardTitle>Shopping List</CardTitle>
            <CardSubtitle>Items organized by category</CardSubtitle>
            <CardBody>
                {isLoading && (
                    <div className="flex justify-center py-8">
                        <Spinner label="Generating shopping list..." />
                    </div>
                )}

                {!isLoading && list && (
                    <>
                        {/* Summary */}
                        <div className="mb-4 p-4 bg-slate-50 rounded-lg">
                            <p className="text-sm text-slate-600">
                                <span className="font-semibold text-slate-900">{list.itemCount}</span> items
                                {list.itemCount !== list.allItems.length && ` (${list.allItems.length - list.itemCount} excluded)`}
                            </p>
                        </div>

                        {/* Categorized items */}
                        <div className="space-y-6">
                            {Object.entries(list.categorized).map(([category, items]) => (
                                <div key={category}>
                                    <h3 className="text-base font-semibold text-slate-900 mb-3 pb-2 border-b-2 border-green-200">
                                        {category}
                                    </h3>
                                    <div className="space-y-2">
                                        {items.map(item => (
                                            <div
                                                key={item.id}
                                                className={`flex items-center gap-3 p-3 rounded-lg border ${item.excluded
                                                    ? "bg-slate-50 border-slate-200 opacity-50"
                                                    : "bg-white border-slate-200 hover:bg-slate-50"
                                                    }`}
                                            >
                                                {/* Checkbox */}
                                                <input
                                                    type="checkbox"
                                                    checked={item.excluded}
                                                    onChange={() => handleExcludeItem(item.id)}
                                                    className="w-5 h-5 rounded cursor-pointer"
                                                />

                                                {/* Item label */}
                                                <div className="flex-1">
                                                    <p className={`font-medium ${item.excluded
                                                        ? "text-slate-500 line-through"
                                                        : "text-slate-900"
                                                        }`}>
                                                        {item.label}
                                                    </p>
                                                    {item.notes && (
                                                        <p className="text-xs text-slate-500 mt-1">{item.notes}</p>
                                                    )}
                                                </div>

                                                {/* Quantity editor */}
                                                <div className="flex items-center gap-2">
                                                    <input
                                                        type="number"
                                                        value={getDisplayQuantity(item)}
                                                        onChange={(e) =>
                                                            handleQuantityChange(item.id, parseFloat(e.target.value) || 0)
                                                        }
                                                        disabled={item.excluded}
                                                        className="w-16 px-2 py-1 text-sm border border-slate-300 rounded text-center disabled:bg-slate-100"
                                                        min="0"
                                                        step="0.1"
                                                    />
                                                    <span className="text-sm text-slate-600 w-12">{item.unit}</span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>

                        {/* Actions */}
                        <div className="mt-6 flex gap-2 pt-4 border-t">
                            <Button onClick={handlePrint} className="flex-1">
                                🖨️ Print
                            </Button>
                            <Button onClick={loadShoppingList} className="flex-1">
                                🔄 Regenerate
                            </Button>
                        </div>
                    </>
                )}

                {!isLoading && !list && !error && (
                    <p className="text-center text-slate-500 py-8">No shopping list generated</p>
                )}
            </CardBody>
        </Card>
    );
}
