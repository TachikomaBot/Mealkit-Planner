import { useState, useRef, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, addIngredient, updateIngredientQuantity, type Ingredient } from '../db';

type Category = Ingredient['category'];

export default function Search() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const query = searchParams.get('q') || '';
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);

  const results = useLiveQuery(async () => {
    if (!query.trim()) return [];
    const normalized = query.toLowerCase();
    const all = await db.ingredients.toArray();
    return all.filter(ing =>
      ing.name.toLowerCase().includes(normalized)
    );
  }, [query]);

  // Show Add modal automatically if no results and query exists, hide when results found
  useEffect(() => {
    if (results !== undefined && query.trim()) {
      if (results.length === 0) {
        setShowAddModal(true);
      } else {
        setShowAddModal(false);
      }
    }
  }, [results, query]);

  const handleToggleExpand = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  return (
    <div className="p-4 max-w-md mx-auto pb-24">
      <header className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate(-1)}
          className="text-gray-600 hover:text-gray-900"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-xl font-bold text-gray-900">
          Search: "{query}"
        </h1>
      </header>

      {results === undefined ? (
        <div className="text-center py-8 text-gray-500">Loading...</div>
      ) : results.length === 0 ? (
        <div className="text-center py-8">
          <p className="text-gray-500 mb-4">No ingredients found matching "{query}"</p>
          <button
            onClick={() => setShowAddModal(true)}
            className="btn btn-primary"
          >
            Add "{query}" to Pantry
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-gray-500 mb-4">
            {results.length} result{results.length !== 1 ? 's' : ''} in pantry
          </p>
          {results.map(ingredient => (
            <div key={ingredient.id}>
              <SearchResultCard
                ingredient={ingredient}
                isExpanded={expandedId === ingredient.id}
                onToggle={() => handleToggleExpand(ingredient.id!)}
              />
              {expandedId === ingredient.id && (
                <AdjusterRow
                  ingredient={ingredient}
                  onClose={() => setExpandedId(null)}
                />
              )}
            </div>
          ))}
        </div>
      )}

      {/* Add Ingredient Modal */}
      {showAddModal && (
        <AddIngredientModal
          onClose={() => setShowAddModal(false)}
          defaultName={query}
        />
      )}
    </div>
  );
}

function SearchResultCard({
  ingredient,
  isExpanded,
  onToggle,
}: {
  ingredient: Ingredient;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  const [stockImageFailed, setStockImageFailed] = useState(false);
  const percentRemaining = Math.round(
    (ingredient.quantityRemaining / ingredient.quantityInitial) * 100
  );
  const isLow = percentRemaining < 25;

  const stockImagePath = `/images/ingredients/${ingredient.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')}.jpg`;

  const categoryEmojis: Record<string, string> = {
    produce: '🥬',
    protein: '🥩',
    dairy: '🧀',
    'dry goods': '🌾',
    spice: '🌶️',
    condiment: '🫙',
    frozen: '🧊',
    other: '📦',
  };

  return (
    <div
      onClick={onToggle}
      className={`card cursor-pointer transition-all ${isExpanded ? 'ring-2 ring-primary-500' : ''}`}
    >
      <div className="flex items-center gap-3">
        {/* Image */}
        <div className="w-16 h-16 rounded-lg overflow-hidden bg-gray-100 flex-shrink-0">
          {!stockImageFailed ? (
            <img
              src={stockImagePath}
              alt={ingredient.name}
              className="w-full h-full object-cover"
              onError={() => setStockImageFailed(true)}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-2xl">
              {categoryEmojis[ingredient.category] || '📦'}
            </div>
          )}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <h3 className="font-medium text-gray-900 truncate">{ingredient.name}</h3>
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <span>
              {ingredient.quantityRemaining} / {ingredient.quantityInitial} {ingredient.unit}
            </span>
            {isLow && (
              <span className="text-amber-500 font-medium">(low)</span>
            )}
          </div>
          {/* Progress bar */}
          <div className="w-full bg-gray-200 rounded-full h-1.5 mt-2">
            <div
              className={`h-1.5 rounded-full transition-all ${isLow ? 'bg-amber-500' : 'bg-primary-600'}`}
              style={{ width: `${percentRemaining}%` }}
            />
          </div>
        </div>

        {/* Percentage */}
        <div className={`text-lg font-bold ${isLow ? 'text-amber-500' : 'text-primary-600'}`}>
          {percentRemaining}%
        </div>
      </div>
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

  useEffect(() => {
    setLocalQuantity(ingredient.quantityRemaining);
  }, [ingredient.quantityRemaining]);

  useEffect(() => {
    if (rowRef.current) {
      setTimeout(() => {
        rowRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 50);
    }
  }, []);

  const handleSave = async () => {
    if (localQuantity !== ingredient.quantityRemaining) {
      await updateIngredientQuantity(ingredient.id!, localQuantity);
    }
    onClose();
  };

  const handleDelete = async () => {
    await db.ingredients.delete(ingredient.id!);
    onClose();
  };

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
    if (isHorizontalSwipe.current === null && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
      isHorizontalSwipe.current = Math.abs(deltaX) > Math.abs(deltaY);
    }
    if (isHorizontalSwipe.current) {
      setSwipeX(deltaX);
    }
  };

  const handleTouchEnd = async () => {
    setIsSwiping(false);
    if (swipeX > 80) {
      await handleSave();
    } else if (swipeX < -80) {
      await handleDelete();
    }
    setSwipeX(0);
    touchStartX.current = null;
    touchStartY.current = null;
    isHorizontalSwipe.current = null;
  };

  const percentRemaining = (localQuantity / ingredient.quantityInitial) * 100;

  const getSwipeStyle = () => {
    if (swipeX > 40) {
      const opacity = Math.min((swipeX - 40) / 80, 0.3);
      return { backgroundColor: `rgba(34, 197, 94, ${opacity})` };
    } else if (swipeX < -40) {
      const opacity = Math.min((-swipeX - 40) / 80, 0.3);
      return { backgroundColor: `rgba(239, 68, 68, ${opacity})` };
    }
    return {};
  };

  return (
    <div
      ref={rowRef}
      className="mt-2 bg-white rounded-xl border-2 border-primary-200 p-4 shadow-lg overflow-hidden"
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
      <div className="flex justify-between items-center mb-3">
        <h3 className="font-semibold text-gray-900">{ingredient.name}</h3>
        <div className="flex gap-2 text-xs text-gray-400">
          <span>← delete</span>
          <span>save →</span>
        </div>
      </div>

      <div className="text-center mb-3">
        <span className="text-2xl font-bold text-primary-600">{localQuantity}</span>
        <span className="text-gray-500 ml-1">{ingredient.unit}</span>
        <span className="text-sm text-gray-400 ml-2">
          ({Math.round(percentRemaining)}% remaining)
        </span>
      </div>

      <div className="flex items-center gap-3 mb-4">
        <span className="text-sm text-gray-400 w-8">0</span>
        <input
          type="range"
          min="0"
          max={ingredient.quantityInitial}
          value={localQuantity}
          onChange={(e) => setLocalQuantity(Number(e.target.value))}
          onTouchStart={(e) => e.stopPropagation()}
          className="flex-1 h-3 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <span className="text-sm text-gray-400 w-12 text-right">
          {ingredient.quantityInitial}
        </span>
      </div>

      <div className="flex gap-2">
        {[0, 25, 50, 75, 100].map(pct => (
          <button
            key={pct}
            onClick={() => setLocalQuantity(Math.round(ingredient.quantityInitial * pct / 100))}
            className={`flex-1 py-1 text-xs rounded-lg transition-colors ${
              Math.round(percentRemaining) === pct
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {pct}%
          </button>
        ))}
      </div>
    </div>
  );
}

function AddIngredientModal({
  onClose,
  defaultName,
}: {
  onClose: () => void;
  defaultName: string;
}) {
  const [name, setName] = useState(defaultName);
  const [quantity, setQuantity] = useState('');
  const [unit, setUnit] = useState<Ingredient['unit']>('g');
  const [category, setCategory] = useState<Category>('other');
  const [dragY, setDragY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const touchStartY = useRef<number | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !quantity) return;

    await addIngredient({
      name,
      brand: null,
      quantityInitial: Number(quantity),
      quantityRemaining: Number(quantity),
      unit,
      category,
      perishable: ['produce', 'protein', 'dairy'].includes(category),
      expiryDate: null,
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
    if (deltaY > 0) {
      setDragY(deltaY);
    }
  };

  const handleTouchEnd = () => {
    setIsDragging(false);
    if (dragY > 100) {
      onClose();
    } else {
      setDragY(0);
    }
    touchStartY.current = null;
  };

  const categories: { value: Category; label: string }[] = [
    { value: 'produce', label: 'Produce' },
    { value: 'protein', label: 'Protein' },
    { value: 'dairy', label: 'Dairy' },
    { value: 'dry goods', label: 'Dry Goods' },
    { value: 'spice', label: 'Spices' },
    { value: 'condiment', label: 'Condiments' },
    { value: 'frozen', label: 'Frozen' },
    { value: 'other', label: 'Other' },
  ];

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-end justify-center z-50 pb-20"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-t-2xl w-full max-w-md p-4 pb-6"
        style={{
          transform: `translateY(${dragY}px)`,
          transition: isDragging ? 'none' : 'transform 0.2s ease-out',
        }}
        onClick={(e) => e.stopPropagation()}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        <div className="flex justify-center mb-3">
          <div className="w-12 h-1 bg-gray-300 rounded-full" />
        </div>
        <h2 className="text-lg font-semibold mb-4">Add Ingredient</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input"
              placeholder="e.g., Chicken Breast"
              autoFocus
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Quantity</label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="input"
                placeholder="500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Unit</label>
              <select
                value={unit}
                onChange={(e) => setUnit(e.target.value as Ingredient['unit'])}
                className="input"
              >
                <option value="g">grams (g)</option>
                <option value="kg">kilograms (kg)</option>
                <option value="ml">milliliters (ml)</option>
                <option value="L">liters (L)</option>
                <option value="units">units</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as Category)}
              className="input"
            >
              {categories.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>

          <button type="submit" className="w-full btn btn-primary">
            Add to Pantry
          </button>
        </form>
      </div>
    </div>
  );
}
