import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import Layout from './components/Layout';
import Home from './pages/Home';
import Pantry from './pages/Pantry';
import Meals from './pages/Meals';
import Profile from './pages/Profile';
import RecipeDetail from './pages/RecipeDetail';
import Settings from './pages/Settings';
import Search from './pages/Search';
import { cleanupExpiredImages } from './db';
import { getImageCacheExpirationDays } from './utils/settings';
import { checkMissedSchedule, setupNotificationListeners, initializeScheduling } from './utils/scheduling';

export default function App() {
  // Clean up expired cached images on app startup using configured expiration
  // Also check if we missed a scheduled generation and set up notification listeners
  useEffect(() => {
    cleanupExpiredImages(getImageCacheExpirationDays());
    initializeScheduling();
    checkMissedSchedule();
    setupNotificationListeners();
  }, []);

  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="pantry" element={<Pantry />} />
        <Route path="meals" element={<Meals />} />
        <Route path="profile" element={<Profile />} />
        <Route path="search" element={<Search />} />
        {/* Redirects from old routes */}
        <Route path="plan" element={<Navigate to="/meals" replace />} />
        <Route path="shop" element={<Navigate to="/meals" replace />} />
      </Route>
      {/* Recipe detail outside layout for full-screen experience */}
      <Route path="/recipe/:id" element={<RecipeDetail />} />
      {/* Settings page outside layout */}
      <Route path="/settings" element={<Settings />} />
    </Routes>
  );
}
