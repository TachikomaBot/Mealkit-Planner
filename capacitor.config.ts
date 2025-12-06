import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.mealplanner.app',
  appName: 'Meal Planner',
  webDir: 'dist',
  server: {
    // For development: connect to your local dev server
    // Comment out for production builds
    // url: 'http://YOUR_IP:5173',
    // cleartext: true,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 2000,
      backgroundColor: '#4f46e5',
      showSpinner: false,
    },
    LocalNotifications: {
      smallIcon: 'ic_stat_icon',
      iconColor: '#4f46e5',
    },
  },
};

export default config;
