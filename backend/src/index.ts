import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import recipesRouter from './routes/recipes.js';
import mealPlanRouter from './routes/mealplan.js';
import imagesRouter from './routes/images.js';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3001;

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' })); // Increase limit for large meal plan responses

// Routes
app.use('/api/recipes', recipesRouter);
app.use('/api/meal-plan', mealPlanRouter);
app.use('/api/images', imagesRouter);

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
  console.log(`Meal Planner API running on http://localhost:${PORT}`);
});
