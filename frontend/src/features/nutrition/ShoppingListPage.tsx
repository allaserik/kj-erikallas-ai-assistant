import { useState } from "react";
import { ShoppingList } from "./ShoppingList";
import { Card, CardBody, CardTitle, CardSubtitle } from "../../shared/ui/Card";
import { TextField } from "../../shared/ui/TextField";
import { Button } from "../../shared/ui/Button";
import { Alert } from "../../shared/ui/Alert";

export default function ShoppingListPage() {
    const [dateRange, setDateRange] = useState({
        startDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
        endDate: new Date().toISOString().split("T")[0],
    });
    const [showList, setShowList] = useState(false);

    const handleGenerateList = () => {
        setShowList(true);
    };

    return (
        <div className="space-y-4 pb-32 md:pb-4">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900">Shopping List Generator</h1>
                    <p className="text-slate-600">Generate organized shopping lists from your meal plans</p>
                </div>
            </div>

            {/* Generator Controls */}
            <Card>
                <CardTitle>Generate Shopping List</CardTitle>
                <CardSubtitle>Select a date range to generate a shopping list from your meal plan</CardSubtitle>
                <CardBody>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <TextField
                            label="Start Date"
                            type="date"
                            value={dateRange.startDate}
                            onChange={(e) =>
                                setDateRange({ ...dateRange, startDate: e.target.value })
                            }
                        />
                        <TextField
                            label="End Date"
                            type="date"
                            value={dateRange.endDate}
                            onChange={(e) =>
                                setDateRange({ ...dateRange, endDate: e.target.value })
                            }
                        />
                    </div>

                    <Alert
                        tone="info"
                        title="How it works"
                        message="Select a date range from your meal plan. All ingredients from recipes in that range will be aggregated, automatically categorized by food type (proteins, dairy, produce, grains, pantry), and you can adjust quantities or exclude items."
                    />

                    <Button
                        onClick={handleGenerateList}
                        className="w-full mt-4"
                    >
                        🛒 Generate Shopping List
                    </Button>
                </CardBody>
            </Card>

            {/* Shopping List Display */}
            {showList && (
                <ShoppingList
                    dateRange={{
                        startDate: dateRange.startDate,
                        endDate: dateRange.endDate,
                    }}
                />
            )}

            {/* Tips Section */}
            <Card>
                <CardTitle>Shopping List Tips</CardTitle>
                <CardBody>
                    <ul className="space-y-2 text-sm text-slate-700">
                        <li>✓ Items are automatically categorized by food type for easier shopping</li>
                        <li>✓ Adjust quantities if you have different portion sizes or shopping habits</li>
                        <li>✓ Exclude items you already have at home</li>
                        <li>✓ Print your list for offline reference in the store</li>
                        <li>✓ Regenerate anytime to recalculate based on updated meal plans</li>
                    </ul>
                </CardBody>
            </Card>
        </div>
    );
}
