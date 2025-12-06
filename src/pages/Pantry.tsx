import { useState, useRef, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, addIngredient, updateIngredientQuantity, type Ingredient } from '../db';

type Category = Ingredient['category'];
type FilterValue = Category | 'all' | 'check-stock';

const categories: { value: FilterValue; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'check-stock', label: 'Check Stock' },
  { value: 'produce', label: 'Produce' },
  { value: 'protein', label: 'Protein' },
  { value: 'dairy', label: 'Dairy' },
  { value: 'dry goods', label: 'Dry Goods' },
  { value: 'spice', label: 'Spices' },
  { value: 'oils', label: 'Oils' },
  { value: 'condiment', label: 'Condiments' },
  { value: 'frozen', label: 'Frozen' },
  { value: 'other', label: 'Other' },
];

// Check if an ingredient needs stock verification
function needsStockCheck(ingredient: Ingredient): boolean {
  const now = new Date();
  const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);
  const threeDaysFromNow = new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000);

  // Only check perishables (protein, dairy, produce)
  if (!['protein', 'dairy', 'produce'].includes(ingredient.category)) {
    return false;
  }

  // If checked within the last 3 days, skip
  if (ingredient.lastStockCheck && ingredient.lastStockCheck > threeDaysAgo) {
    return false;
  }

  // Check if expiring within 3 days
  if (ingredient.expiryDate && ingredient.expiryDate <= threeDaysFromNow) {
    return true;
  }

  // Check if added more than 3 days ago (might be stale)
  if (ingredient.dateAdded <= threeDaysAgo) {
    return true;
  }

  // Check if partially consumed (auto-deduction happened)
  if (ingredient.quantityRemaining < ingredient.quantityInitial) {
    return true;
  }

  return false;
}

export default function Pantry() {
  const location = useLocation();
  const [showAddForm, setShowAddForm] = useState(false);

  // Check if we should start with a specific filter (e.g., from notification deep link)
  const getInitialFilter = (): FilterValue => {
    // First check location state (from React Router navigation)
    const stateFilter = (location.state as { filter?: FilterValue } | null)?.filter;
    if (stateFilter) return stateFilter;

    // Then check localStorage (from notification tap)
    const storedFilter = localStorage.getItem('pantry_initial_filter');
    if (storedFilter) {
      localStorage.removeItem('pantry_initial_filter'); // Clear after reading
      return storedFilter as FilterValue;
    }

    return 'all';
  };

  const [filter, setFilter] = useState<FilterValue>(getInitialFilter);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Swipe handling refs
  const containerRef = useRef<HTMLDivElement>(null);
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);

  const ingredients = useLiveQuery(async () => {
    if (filter === 'all') {
      return db.ingredients.orderBy('name').toArray();
    }
    if (filter === 'check-stock') {
      // Get all perishables and filter to those needing check
      const all = await db.ingredients.toArray();
      return all.filter(needsStockCheck).sort((a, b) => a.name.localeCompare(b.name));
    }
    return db.ingredients.where('category').equals(filter).sortBy('name');
  }, [filter]);

  // Count items needing stock check (for badge display)
  const checkStockCount = useLiveQuery(async () => {
    const all = await db.ingredients.toArray();
    return all.filter(needsStockCheck).length;
  });

  const handleToggleExpand = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  // Find the expanded item's index and determine where to place the adjuster
  const expandedIndex = ingredients?.findIndex(item => item.id === expandedId) ?? -1;
  const adjusterRowIndex = expandedIndex >= 0 ? Math.floor(expandedIndex / 2) : -1;

  // Ref for the category carousel to exclude from swipe detection
  const carouselRef = useRef<HTMLDivElement>(null);

  // Use document-level touch tracking for reliable swipe detection
  useEffect(() => {
    const handleDocTouchStart = (e: TouchEvent) => {
      // Ignore touches that start on the category carousel
      if (carouselRef.current?.contains(e.target as Node)) {
        touchStartX.current = null;
        touchStartY.current = null;
        return;
      }
      touchStartX.current = e.touches[0].clientX;
      touchStartY.current = e.touches[0].clientY;
    };

    const handleDocTouchEnd = (e: TouchEvent) => {
      if (touchStartX.current === null || touchStartY.current === null) return;

      const touchEndX = e.changedTouches[0].clientX;
      const touchEndY = e.changedTouches[0].clientY;
      const deltaX = touchEndX - touchStartX.current;
      const deltaY = touchEndY - touchStartY.current;

      // Only trigger swipe if horizontal movement is greater than vertical
      if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 50) {
        const currentIndex = categories.findIndex(c => c.value === filter);

        if (deltaX < 0 && currentIndex < categories.length - 1) {
          setFilter(categories[currentIndex + 1].value);
          setExpandedId(null);
        } else if (deltaX > 0 && currentIndex > 0) {
          setFilter(categories[currentIndex - 1].value);
          setExpandedId(null);
        }
      }

      touchStartX.current = null;
      touchStartY.current = null;
    };

    document.addEventListener('touchstart', handleDocTouchStart, { passive: true });
    document.addEventListener('touchend', handleDocTouchEnd, { passive: true });

    return () => {
      document.removeEventListener('touchstart', handleDocTouchStart);
      document.removeEventListener('touchend', handleDocTouchEnd);
    };
  }, [filter]);

  return (
    <div ref={containerRef} className="min-h-full">
      <div className="p-4 max-w-lg mx-auto w-full pb-4">
        <header className="mb-4">
          <h1 className="text-2xl font-bold text-gray-900">Pantry</h1>
        </header>

        {/* Category filter carousel */}
        <div
          ref={carouselRef}
          className="flex gap-2 overflow-x-auto pb-2 mb-4 -mx-4 px-4 scrollbar-hide"
        >
          {categories.map(({ value, label }) => (
            <button
              key={value}
              onClick={() => {
                setFilter(value);
                setExpandedId(null);
              }}
              className={`px-3 py-1 rounded-full text-sm whitespace-nowrap transition-colors flex items-center gap-1 ${
                filter === value
                  ? 'bg-primary-600 text-white'
                  : value === 'check-stock' && checkStockCount && checkStockCount > 0
                  ? 'bg-amber-100 text-amber-800 border border-amber-300'
                  : 'bg-gray-200 text-gray-700'
              }`}
            >
              {label}
              {value === 'check-stock' && checkStockCount !== undefined && checkStockCount > 0 && (
                <span className={`ml-1 px-1.5 py-0.5 text-xs rounded-full ${
                  filter === value ? 'bg-white/20' : 'bg-amber-500 text-white'
                }`}>
                  {checkStockCount}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Ingredient grid */}
        {ingredients?.length === 0 ? (
          <div className="text-center py-12 text-gray-500">
            {filter === 'check-stock' ? (
              <>
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                  <span className="text-3xl">✓</span>
                </div>
                <p className="mb-2 text-green-700 font-medium">All stock verified!</p>
                <p className="text-sm">No perishables need checking right now.</p>
              </>
            ) : (
              <>
                <p className="mb-2">Your pantry is empty</p>
                <p className="text-sm">Add ingredients to get started</p>
              </>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            {/* Render ingredients in pairs with potential adjuster rows */}
            {ingredients && renderIngredientGrid(
              ingredients,
              expandedId,
              expandedIndex,
              adjusterRowIndex,
              handleToggleExpand,
              () => setExpandedId(null)
            )}
          </div>
        )}
      </div>

      {/* Floating add button */}
      <button
        onClick={() => setShowAddForm(true)}
        className="fixed bottom-24 right-4 w-14 h-14 bg-primary-600 text-white rounded-full shadow-lg flex items-center justify-center text-3xl hover:bg-primary-700 transition-colors z-20"
        aria-label="Add ingredient"
      >
        +
      </button>

      {/* Add form modal */}
      {showAddForm && (
        <AddIngredientModal
          onClose={() => setShowAddForm(false)}
          defaultCategory={filter === 'all' || filter === 'check-stock' ? 'other' : filter}
        />
      )}
    </div>
  );
}

// Render the grid with adjuster rows inserted at the right position
function renderIngredientGrid(
  ingredients: Ingredient[],
  expandedId: number | null,
  expandedIndex: number,
  adjusterRowIndex: number,
  onToggle: (id: number) => void,
  onClose: () => void
): React.ReactNode[] {
  const rows: React.ReactNode[] = [];
  const expandedItem = expandedIndex >= 0 ? ingredients[expandedIndex] : null;

  for (let i = 0; i < ingredients.length; i += 2) {
    const rowIndex = i / 2;
    const leftItem = ingredients[i];
    const rightItem = ingredients[i + 1];

    // Determine if either item in this row is the expanded one
    const leftExpanded = leftItem?.id === expandedId;
    const rightExpanded = rightItem?.id === expandedId;
    const rowHasExpanded = leftExpanded || rightExpanded;

    rows.push(
      <div key={`row-${rowIndex}`} className="grid grid-cols-2 gap-3">
        <IngredientCard
          ingredient={leftItem}
          isExpanded={leftExpanded}
          isInactiveInPair={rowHasExpanded && !leftExpanded}
          onToggle={() => onToggle(leftItem.id!)}
        />
        {rightItem ? (
          <IngredientCard
            ingredient={rightItem}
            isExpanded={rightExpanded}
            isInactiveInPair={rowHasExpanded && !rightExpanded}
            onToggle={() => onToggle(rightItem.id!)}
          />
        ) : (
          <div /> // Empty placeholder for odd number of items
        )}
      </div>
    );

    // Insert adjuster row after this pair if one of them is expanded
    if (rowIndex === adjusterRowIndex && expandedItem) {
      rows.push(
        <AdjusterRow
          key={`adjuster-${rowIndex}`}
          ingredient={expandedItem}
          onClose={onClose}
        />
      );
    }
  }

  return rows;
}

// Helper to generate stock image filename from ingredient name
function getStockImagePath(name: string): string {
  // Convert "All-Purpose Flour" -> "all-purpose-flour.jpg"
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
  return `/images/ingredients/${slug}.jpg`;
}

function IngredientCard({
  ingredient,
  isExpanded,
  isInactiveInPair,
  onToggle,
}: {
  ingredient: Ingredient;
  isExpanded: boolean;
  isInactiveInPair: boolean;
  onToggle: () => void;
}) {
  const [stockImageFailed, setStockImageFailed] = useState(false);

  const percentRemaining =
    (ingredient.quantityRemaining / ingredient.quantityInitial) * 100;

  // Grayscale increases as quantity decreases (0% remaining = 100% grayscale)
  const grayscalePercent = Math.min(100, Math.max(0, 100 - percentRemaining));

  // Apply full grayscale to inactive pair item
  const finalGrayscale = isInactiveInPair ? 100 : grayscalePercent;

  // Stock image path for this ingredient
  const stockImagePath = getStockImagePath(ingredient.name);

  return (
    <div
      onClick={onToggle}
      className="relative cursor-pointer transition-all duration-300"
    >
      {/* Selection highlight - outside the card so it doesn't overlap water level */}
      {isExpanded && (
        <div className="absolute -inset-1 rounded-2xl ring-2 ring-tomato pointer-events-none z-30" />
      )}

      {/* Water level border - outer container with padding creates border area */}
      <div className="relative p-1 rounded-xl overflow-hidden">
        {/* Border background - grey (empty) */}
        <div className="absolute inset-0 rounded-xl bg-alabaster-300" />

        {/* Water fill - blue portion from bottom */}
        <div
          className="absolute bottom-0 left-0 right-0 rounded-b-xl transition-all duration-500"
          style={{
            height: `${percentRemaining}%`,
            background: 'linear-gradient(to top, #009fb7, #22d3ee)',
          }}
        />

        {/* Card content - sits on top of border */}
        <div
          className="relative bg-white rounded-lg overflow-hidden"
          style={{
            filter: `grayscale(${finalGrayscale}%)`,
            transition: 'filter 0.3s ease',
          }}
        >
          {/* Image area */}
          <div className="aspect-[3/4] bg-alabaster-100 relative">
            {!stockImageFailed ? (
              <img
                src={stockImagePath}
                alt={ingredient.name}
                className="w-full h-full object-cover"
                onError={() => setStockImageFailed(true)}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-alabaster-400">
                <span className="text-4xl opacity-30">
                  {getCategoryEmoji(ingredient.category)}
                </span>
              </div>
            )}

            {/* Overlay gradient for text readability */}
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-black/70 to-transparent" />

            {/* Name and quantity overlay */}
            <div className="absolute inset-x-0 bottom-0 p-3 text-white">
              <h3 className="font-medium text-sm leading-tight truncate">
                {ingredient.name}
              </h3>
              <p className="text-xs text-white/80 mt-0.5">
                {ingredient.quantityRemaining} / {ingredient.quantityInitial} {ingredient.unit}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Status indicators */}
      {percentRemaining < 20 && (
        <div className="absolute top-2 right-2 bg-mustard text-alabaster-900 text-xs px-2 py-0.5 rounded-full font-medium z-20">
          Low
        </div>
      )}
    </div>
  );
}

function AdjusterRow({
  ingredient,
  onClose,
}: {
  ingredient: Ingredient;
  onClose: () => void;
}) {
  const [localQuantity, setLocalQuantity] = useState(ingredient.quantityRemaining);
  const rowRef = useRef<HTMLDivElement>(null);
  const [swipeX, setSwipeX] = useState(0);
  const [isSwiping, setIsSwiping] = useState(false);
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);
  const isHorizontalSwipe = useRef<boolean | null>(null);

  // Reset local quantity when ingredient changes
  useEffect(() => {
    setLocalQuantity(ingredient.quantityRemaining);
  }, [ingredient.quantityRemaining]);

  // Auto-scroll into view when mounted - use 'center' for better visibility
  useEffect(() => {
    if (rowRef.current) {
      // Small delay to ensure animation has started
      setTimeout(() => {
        rowRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 50);
    }
  }, []);

  const handleSliderChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    setLocalQuantity(Number(e.target.value));
  };

  const handleSave = async () => {
    try {
      // Always mark as checked when saving, even if quantity unchanged
      await updateIngredientQuantity(ingredient.id!, localQuantity, true);
    } catch (err) {
      console.error('Failed to update quantity:', err);
    }
    onClose();
  };

  const handleDelete = async () => {
    await db.ingredients.delete(ingredient.id!);
    onClose();
  };

  // Swipe handling
  const handleTouchStart = (e: React.TouchEvent) => {
    e.stopPropagation();
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
    isHorizontalSwipe.current = null;
    setIsSwiping(true);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (touchStartX.current === null || touchStartY.current === null) return;

    const deltaX = e.touches[0].clientX - touchStartX.current;
    const deltaY = e.touches[0].clientY - touchStartY.current;

    // Determine swipe direction on first significant movement
    if (isHorizontalSwipe.current === null && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
      isHorizontalSwipe.current = Math.abs(deltaX) > Math.abs(deltaY);
    }

    // Only track horizontal swipes
    if (isHorizontalSwipe.current) {
      setSwipeX(deltaX);
    }
  };

  const handleTouchEnd = async () => {
    setIsSwiping(false);

    // Swipe right > 80px = save
    if (swipeX > 80) {
      await handleSave();
    }
    // Swipe left > 80px = delete
    else if (swipeX < -80) {
      await handleDelete();
    }

    setSwipeX(0);
    touchStartX.current = null;
    touchStartY.current = null;
    isHorizontalSwipe.current = null;
  };

  const percentRemaining = (localQuantity / ingredient.quantityInitial) * 100;

  // Visual feedback colors based on swipe direction
  const getSwipeStyle = () => {
    if (swipeX > 40) {
      // Swiping right - green tint for save
      const opacity = Math.min((swipeX - 40) / 80, 0.3);
      return { backgroundColor: `rgba(34, 197, 94, ${opacity})` };
    } else if (swipeX < -40) {
      // Swiping left - red tint for delete
      const opacity = Math.min((-swipeX - 40) / 80, 0.3);
      return { backgroundColor: `rgba(239, 68, 68, ${opacity})` };
    }
    return {};
  };

  return (
    <div
      ref={rowRef}
      className="bg-white rounded-xl border-2 border-primary-200 p-4 shadow-lg animate-slide-up overflow-hidden"
      style={{
        transform: `translateX(${swipeX}px)`,
        transition: isSwiping ? 'none' : 'transform 0.2s ease-out',
        ...getSwipeStyle(),
      }}
      onClick={(e) => e.stopPropagation()}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      {/* Header with ingredient name */}
      <div className="flex justify-between items-center mb-3">
        <h3 className="font-semibold text-gray-900">{ingredient.name}</h3>
        {/* Swipe hints */}
        <div className="flex gap-2 text-xs text-gray-400">
          <span>← delete</span>
          <span>save →</span>
        </div>
      </div>

      {/* Quantity display */}
      <div className="text-center mb-3">
        <span className="text-2xl font-bold text-primary-600">{localQuantity}</span>
        <span className="text-gray-500 ml-1">{ingredient.unit}</span>
        <span className="text-sm text-gray-400 ml-2">
          ({Math.round(percentRemaining)}% remaining)
        </span>
      </div>

      {/* Slider */}
      <div className="flex items-center gap-3">
        <span className="text-sm text-gray-400 w-8">0</span>
        <input
          type="range"
          min="0"
          max={ingredient.quantityInitial}
          value={localQuantity}
          onChange={handleSliderChange}
          onTouchStart={(e) => e.stopPropagation()}
          className="flex-1 h-3 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <span className="text-sm text-gray-400 w-12 text-right">
          {ingredient.quantityInitial}
        </span>
      </div>
    </div>
  );
}

function AddIngredientModal({
  onClose,
  defaultCategory,
}: {
  onClose: () => void;
  defaultCategory: Category;
}) {
  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('');
  const [unit, setUnit] = useState<Ingredient['unit']>('g');
  const [category, setCategory] = useState<Category>(defaultCategory);
  const [expiryDays, setExpiryDays] = useState<string>('');
  const [dragY, setDragY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const touchStartY = useRef<number | null>(null);

  const isPerishable = ['produce', 'protein', 'dairy'].includes(category);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !quantity) return;

    // Calculate expiry date if days provided
    let expiryDate: Date | null = null;
    if (expiryDays && Number(expiryDays) > 0) {
      expiryDate = new Date();
      expiryDate.setDate(expiryDate.getDate() + Number(expiryDays));
    }

    await addIngredient({
      name,
      brand: null,
      quantityInitial: Number(quantity),
      quantityRemaining: Number(quantity),
      unit,
      category,
      perishable: isPerishable,
      expiryDate,
    });

    onClose();
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartY.current = e.touches[0].clientY;
    setIsDragging(true);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (touchStartY.current === null) return;
    const deltaY = e.touches[0].clientY - touchStartY.current;
    // Only allow dragging down
    if (deltaY > 0) {
      setDragY(deltaY);
    }
  };

  const handleTouchEnd = () => {
    setIsDragging(false);
    // Close if dragged more than 100px down
    if (dragY > 100) {
      onClose();
    } else {
      setDragY(0);
    }
    touchStartY.current = null;
  };

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-end justify-center z-50 pb-20"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-t-2xl w-full max-w-md p-4 pb-6 animate-slide-up"
        style={{
          transform: `translateY(${dragY}px)`,
          transition: isDragging ? 'none' : 'transform 0.2s ease-out',
        }}
        onClick={(e) => e.stopPropagation()}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        {/* Drag handle */}
        <div className="flex justify-center mb-3">
          <div className="w-12 h-1 bg-gray-300 rounded-full" />
        </div>
        <h2 className="text-lg font-semibold mb-4">Add Ingredient</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input"
              placeholder="e.g., All-purpose flour"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Quantity
              </label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="input"
                placeholder="500"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Unit
              </label>
              <select
                value={unit}
                onChange={(e) => setUnit(e.target.value as Ingredient['unit'])}
                className="input"
              >
                <option value="g">grams (g)</option>
                <option value="ml">milliliters (ml)</option>
                <option value="units">units</option>
                <option value="bunch">bunch</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Category
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as Category)}
              className="input"
            >
              {categories.slice(1).map(({ value, label }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          {/* Expiry date field - only for perishable categories */}
          {isPerishable && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Expires in (days)
              </label>
              <input
                type="number"
                value={expiryDays}
                onChange={(e) => setExpiryDays(e.target.value)}
                className="input"
                placeholder="7"
                min="1"
                max="365"
              />
              <p className="text-xs text-gray-500 mt-1">
                Leave empty for no specific expiry date
              </p>
            </div>
          )}

          <button type="submit" className="w-full btn btn-primary">
            Add to Pantry
          </button>
        </form>
      </div>
    </div>
  );
}

function getCategoryEmoji(category: Category): string {
  const emojis: Record<Category, string> = {
    produce: '🥬',
    protein: '🥩',
    dairy: '🧀',
    'dry goods': '🌾',
    spice: '🌶️',
    oils: '🫒',
    condiment: '🫙',
    frozen: '🧊',
    other: '📦',
  };
  return emojis[category] || '📦';
}
