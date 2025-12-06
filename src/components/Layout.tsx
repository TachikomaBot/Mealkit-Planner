import { NavLink, Outlet } from 'react-router-dom';
import SearchBar from './SearchBar';

const navItems = [
  { to: '/', label: 'Home', icon: '🏠' },
  { to: '/pantry', label: 'Pantry', icon: '🥫' },
  { to: '/meals', label: 'Meals', icon: '📅' },
  { to: '/profile', label: 'Profile', icon: '👤' },
];

export default function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      {/* Search bar */}
      <div className="sticky top-0 z-40 bg-white border-b border-gray-200 px-4 py-2">
        <SearchBar />
      </div>

      {/* Main content */}
      <main className="flex-1 pb-24 overflow-auto">
        <Outlet />
      </main>

      {/* Bottom navigation */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-alabaster-300 px-4 py-3 z-50">
        <div className="max-w-md mx-auto flex justify-around">
          {navItems.map(({ to, label, icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex flex-col items-center px-3 py-1 rounded-lg transition-colors ${
                  isActive
                    ? 'text-primary-600'
                    : 'text-gray-500 hover:text-gray-700'
                }`
              }
            >
              <span className="text-xl">{icon}</span>
              <span className="text-xs mt-0.5">{label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  );
}
