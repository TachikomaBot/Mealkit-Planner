import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, addIngredient, type ShoppingListItem } from '../db';
import { formatQuantity } from '../utils/settings';

// Category display order for logical store navigation
const CATEGORY_ORDER = ['Produce', 'Protein', 'Dairy', 'Bakery', 'Pantry', 'Spices', 'Oils', 'Frozen', 'Household', 'Other'];

// Categories available for manual items
const MANUAL_ITEM_CATEGORIES = [
  { value: 'Household', label: 'Household (paper, cleaning, etc.)' },
  { value: 'Produce', label: 'Produce' },
  { value: 'Protein', label: 'Protein' },
  { value: 'Dairy', label: 'Dairy' },
  { value: 'Bakery', label: 'Bakery' },
  { value: 'Pantry', label: 'Pantry' },
  { value: 'Oils', label: 'Oils' },
  { value: 'Frozen', label: 'Frozen' },
  { value: 'Other', label: 'Other' },
];

export default function Shop() {
  const navigate = useNavigate();
  const [shoppingMode, setShoppingMode] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [showAddItem, setShowAddItem] = useState(false);
  const [newItemName, setNewItemName] = useState('');
  const [newItemQuantity, setNewItemQuantity] = useState('1');
  const [newItemUnit, setNewItemUnit] = useState('units');
  const [newItemCategory, setNewItemCategory] = useState('Household');
  const [isGenerating, setIsGenerating] = useState(() =>
    localStorage.getItem('shopping_list_generating') === 'true'
  );

  // Poll for generation completion
  useEffect(() => {
    if (!isGenerating) return;

    const checkGenerationStatus = () => {
      const stillGenerating = localStorage.getItem('shopping_list_generating') === 'true';
      if (!stillGenerating) {
        setIsGenerating(false);
      }
    };

    // Check immediately and then every 500ms
    const interval = setInterval(checkGenerationStatus, 500);
    return () => clearInterval(interval);
  }, [isGenerating]);

  const shoppingList = useLiveQuery(() =>
    db.shoppingList.orderBy('category').toArray()
  );

  // Sort categories by predefined order
  const groupedItems = shoppingList?.reduce((acc, item) => {
    if (!acc[item.category]) acc[item.category] = [];
    acc[item.category].push(item);
    return acc;
  }, {} as Record<string, ShoppingListItem[]>);

  const sortedCategories = groupedItems
    ? Object.keys(groupedItems).sort((a, b) => {
        const aIndex = CATEGORY_ORDER.indexOf(a);
        const bIndex = CATEGORY_ORDER.indexOf(b);
        if (aIndex === -1 && bIndex === -1) return a.localeCompare(b);
        if (aIndex === -1) return 1;
        if (bIndex === -1) return -1;
        return aIndex - bIndex;
      })
    : [];

  const checkedCount = shoppingList?.filter((i) => i.checked).length ?? 0;
  const totalCount = shoppingList?.length ?? 0;
  const allChecked = totalCount > 0 && checkedCount === totalCount;

  // Add a manual item to the shopping list
  const handleAddManualItem = async () => {
    if (!newItemName.trim()) return;

    const quantity = parseFloat(newItemQuantity) || 1;

    await db.shoppingList.add({
      mealPlanId: 0, // 0 indicates a manual item
      ingredientName: newItemName.trim(),
      quantity,
      unit: newItemUnit,
      category: newItemCategory,
      checked: false,
      inCart: false,
      notes: null,
    });

    // Reset form
    setNewItemName('');
    setNewItemQuantity('1');
    setNewItemUnit('units');
    setNewItemCategory('Household');
    setShowAddItem(false);
  };

  // Complete shopping - add checked items to pantry and clear the list
  const handleCompleteShoppingTrip = async () => {
    if (!shoppingList) return;

    setCompleting(true);
    try {
      const checkedItems = shoppingList.filter(item => item.checked);

      // Add checked items to pantry (skip manual items like household goods)
      for (const item of checkedItems) {
        // Skip manual items (mealPlanId: 0) - these are non-food items like paper towels
        if (item.mealPlanId === 0) continue;
        // Map shopping category to ingredient category
        const category = mapToIngredientCategory(item.category);

        await addIngredient({
          name: item.ingredientName,
          brand: null,
          quantityInitial: item.quantity,
          quantityRemaining: item.quantity,
          unit: item.unit as 'g' | 'ml' | 'units' | 'bunch',
          category,
          perishable: ['Produce', 'Protein', 'Dairy'].includes(item.category),
          expiryDate: getDefaultExpiry(item.category),
        });
      }

      // Clear the entire shopping list
      await db.shoppingList.clear();

      // Exit shopping mode
      setShoppingMode(false);

      // Navigate to home
      navigate('/');
    } catch (err) {
      console.error('Failed to complete shopping:', err);
    } finally {
      setCompleting(false);
    }
  };

  // Show loading state when generating shopping list
  if (isGenerating || (!shoppingList || shoppingList.length === 0)) {
    return (
      <div className="p-4 max-w-md mx-auto">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Shopping List</h1>
        </header>

        {isGenerating ? (
          <div className="text-center py-16">
            <div className="w-20 h-20 mx-auto mb-6 relative">
              <div className="absolute inset-0 border-4 border-primary-200 rounded-full"></div>
              <div className="absolute inset-0 border-4 border-primary-600 rounded-full border-t-transparent animate-spin"></div>
              <span className="absolute inset-0 flex items-center justify-center text-3xl">🛒</span>
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              Building Shopping List
            </h2>
            <p className="text-gray-600 text-sm max-w-xs mx-auto">
              AI is consolidating ingredients into practical shopping quantities...
            </p>
          </div>
        ) : (
          <div className="text-center py-12">
            <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span className="text-3xl">🛒</span>
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              No shopping list yet
            </h2>
            <p className="text-gray-600 text-sm mb-4">
              Create a meal plan to generate your shopping list, or add items manually
            </p>
            <div className="flex flex-col gap-2 items-center">
              <button
                onClick={() => navigate('/plan')}
                className="btn btn-primary"
              >
                Create Meal Plan
              </button>
              <button
                onClick={() => setShowAddItem(true)}
                className="btn btn-secondary"
              >
                + Add Item Manually
              </button>
            </div>
          </div>
        )}

        {/* Add Item Modal */}
        {showAddItem && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-xl w-full max-w-sm p-4">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Add Item</h2>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Item Name
                  </label>
                  <input
                    type="text"
                    value={newItemName}
                    onChange={(e) => setNewItemName(e.target.value)}
                    placeholder="e.g., Paper towels"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                    autoFocus
                  />
                </div>

                <div className="flex gap-3">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Quantity
                    </label>
                    <input
                      type="number"
                      value={newItemQuantity}
                      onChange={(e) => setNewItemQuantity(e.target.value)}
                      min="0.1"
                      step="0.5"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                    />
                  </div>
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Unit
                    </label>
                    <select
                      value={newItemUnit}
                      onChange={(e) => setNewItemUnit(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                    >
                      <option value="units">units</option>
                      <option value="g">g</option>
                      <option value="ml">ml</option>
                      <option value="bunch">bunch</option>
                      <option value="pack">pack</option>
                      <option value="box">box</option>
                      <option value="bottle">bottle</option>
                      <option value="roll">roll</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Category
                  </label>
                  <select
                    value={newItemCategory}
                    onChange={(e) => setNewItemCategory(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                  >
                    {MANUAL_ITEM_CATEGORIES.map((cat) => (
                      <option key={cat.value} value={cat.value}>
                        {cat.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  onClick={() => setShowAddItem(false)}
                  className="flex-1 btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  onClick={handleAddManualItem}
                  disabled={!newItemName.trim()}
                  className="flex-1 btn btn-primary disabled:opacity-50"
                >
                  Add Item
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="flex justify-between items-center mb-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Shopping List</h1>
          <p className="text-sm text-gray-500">
            {checkedCount} of {totalCount} items
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowAddItem(true)}
            className="btn btn-secondary"
          >
            + Add
          </button>
          <button
            onClick={() => setShoppingMode(!shoppingMode)}
            className={`btn ${shoppingMode ? 'btn-primary' : 'btn-secondary'}`}
          >
            {shoppingMode ? 'Exit Shopping' : 'Start Shopping'}
          </button>
        </div>
      </header>

      {shoppingMode && (
        <div className="mb-4 p-3 bg-primary-50 border border-primary-200 rounded-lg">
          <p className="text-sm text-primary-800">
            <strong>Shopping Mode Active</strong> — Tap items to mark as found.
            Report issues for real-time substitution suggestions.
          </p>
        </div>
      )}

      {/* Add Item Modal */}
      {showAddItem && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl w-full max-w-sm p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Add Item</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Item Name
                </label>
                <input
                  type="text"
                  value={newItemName}
                  onChange={(e) => setNewItemName(e.target.value)}
                  placeholder="e.g., Paper towels"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                  autoFocus
                />
              </div>

              <div className="flex gap-3">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Quantity
                  </label>
                  <input
                    type="number"
                    value={newItemQuantity}
                    onChange={(e) => setNewItemQuantity(e.target.value)}
                    min="0.1"
                    step="0.5"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Unit
                  </label>
                  <select
                    value={newItemUnit}
                    onChange={(e) => setNewItemUnit(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                  >
                    <option value="units">units</option>
                    <option value="g">g</option>
                    <option value="ml">ml</option>
                    <option value="bunch">bunch</option>
                    <option value="pack">pack</option>
                    <option value="box">box</option>
                    <option value="bottle">bottle</option>
                    <option value="roll">roll</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Category
                </label>
                <select
                  value={newItemCategory}
                  onChange={(e) => setNewItemCategory(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                >
                  {MANUAL_ITEM_CATEGORIES.map((cat) => (
                    <option key={cat.value} value={cat.value}>
                      {cat.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setShowAddItem(false)}
                className="flex-1 btn btn-secondary"
              >
                Cancel
              </button>
              <button
                onClick={handleAddManualItem}
                disabled={!newItemName.trim()}
                className="flex-1 btn btn-primary disabled:opacity-50"
              >
                Add Item
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Progress bar */}
      <div className="mb-6">
        <div className="w-full bg-gray-200 rounded-full h-2">
          <div
            className="bg-primary-600 h-2 rounded-full transition-all"
            style={{
              width: `${totalCount > 0 ? (checkedCount / totalCount) * 100 : 0}%`,
            }}
          />
        </div>
      </div>

      {/* Shopping list by category */}
      <div className="space-y-6 pb-24">
        {sortedCategories.map((category) => (
          <section key={category}>
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-2">
              {category}
            </h2>
            <div className="space-y-2">
              {groupedItems![category].map((item) => (
                <ShoppingItem
                  key={item.id}
                  item={item}
                  shoppingMode={shoppingMode}
                />
              ))}
            </div>
          </section>
        ))}
      </div>

      {/* Shopping mode actions */}
      {shoppingMode && (
        <div className="fixed bottom-20 left-4 right-4 max-w-md mx-auto">
          <div className="bg-white border border-gray-200 rounded-xl shadow-lg p-3">
            {allChecked ? (
              <button
                onClick={handleCompleteShoppingTrip}
                disabled={completing}
                className="w-full btn btn-primary"
              >
                {completing ? 'Adding to Pantry...' : 'Complete Shopping Trip'}
              </button>
            ) : (
              <p className="text-center text-sm text-gray-600">
                Tap items to mark as found ({checkedCount}/{totalCount})
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ShoppingItem({
  item,
  shoppingMode,
}: {
  item: ShoppingListItem;
  shoppingMode: boolean;
}) {
  const handleToggle = async () => {
    await db.shoppingList.update(item.id!, { checked: !item.checked });
  };

  return (
    <div
      onClick={shoppingMode ? handleToggle : undefined}
      className={`card flex items-center gap-3 ${
        shoppingMode ? 'cursor-pointer active:bg-gray-50' : ''
      } ${item.checked ? 'opacity-50' : ''}`}
    >
      <div
        className={`w-6 h-6 rounded-full border-2 flex items-center justify-center ${
          item.checked
            ? 'bg-primary-600 border-primary-600 text-white'
            : 'border-gray-300'
        }`}
      >
        {item.checked && <span className="text-sm">✓</span>}
      </div>
      <div className="flex-1">
        <p
          className={`font-medium ${
            item.checked ? 'line-through text-gray-400' : 'text-gray-900'
          }`}
        >
          {item.ingredientName}
        </p>
        <p className="text-sm text-gray-500">
          {formatQuantity(item.quantity)} {item.unit}
        </p>
      </div>
      {item.inCart && !item.checked && (
        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded">
          In cart
        </span>
      )}
    </div>
  );
}

// Map shopping list category to ingredient category
function mapToIngredientCategory(shopCategory: string): 'dry goods' | 'dairy' | 'produce' | 'protein' | 'condiment' | 'spice' | 'oils' | 'frozen' | 'other' {
  switch (shopCategory) {
    case 'Produce':
      return 'produce';
    case 'Protein':
      return 'protein';
    case 'Dairy':
      return 'dairy';
    case 'Pantry':
      return 'dry goods';
    case 'Spices':
      return 'spice';
    case 'Oils':
      return 'oils';
    case 'Frozen':
      return 'frozen';
    case 'Bakery':
      return 'dry goods';
    default:
      return 'other';
  }
}

// Get default expiry date based on category
function getDefaultExpiry(category: string): Date | null {
  const now = new Date();

  switch (category) {
    case 'Produce':
      // Produce expires in 7 days
      return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    case 'Protein':
      // Protein expires in 3 days (refrigerated) or assume frozen = 30 days
      return new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    case 'Dairy':
      // Dairy expires in 14 days
      return new Date(now.getTime() + 14 * 24 * 60 * 60 * 1000);
    case 'Bakery':
      // Bread expires in 5 days
      return new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    default:
      // Non-perishable items don't expire
      return null;
  }
}
