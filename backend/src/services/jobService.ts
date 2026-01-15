import { randomUUID } from 'crypto';
import type {
  MealPlanJob,
  ProgressEvent,
  MealPlanResponse,
  GroceryPolishJob,
  GroceryPolishProgress,
  GroceryPolishResponse,
  PantryCategorizeJob,
  PantryCategorizeProgress,
  PantryCategorizeResponse
} from '../types.js';

// In-memory job storage (jobs expire after 30 minutes)
const jobs = new Map<string, MealPlanJob>();
const groceryPolishJobs = new Map<string, GroceryPolishJob>();
const pantryCategorizeJobs = new Map<string, PantryCategorizeJob>();
const JOB_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

// ============================================================================
// Meal Plan Jobs
// ============================================================================

/**
 * Create a new job
 */
export function createJob(): MealPlanJob {
  const job: MealPlanJob = {
    id: randomUUID(),
    status: 'pending',
    progress: null,
    result: null,
    error: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  jobs.set(job.id, job);
  cleanupExpiredJobs();
  return job;
}

/**
 * Get a job by ID
 */
export function getJob(id: string): MealPlanJob | undefined {
  return jobs.get(id);
}

/**
 * Update job status to running
 */
export function startJob(id: string): void {
  const job = jobs.get(id);
  if (job) {
    job.status = 'running';
    job.updatedAt = new Date();
  }
}

/**
 * Update job progress
 */
export function updateJobProgress(id: string, progress: ProgressEvent): void {
  const job = jobs.get(id);
  if (job) {
    job.progress = progress;
    job.updatedAt = new Date();
  }
}

/**
 * Complete a job with result
 */
export function completeJob(id: string, result: MealPlanResponse): void {
  const job = jobs.get(id);
  if (job) {
    job.status = 'completed';
    job.result = result;
    job.progress = { phase: 'complete', current: 1, total: 1 };
    job.updatedAt = new Date();
  }
}

/**
 * Fail a job with error
 */
export function failJob(id: string, error: string): void {
  const job = jobs.get(id);
  if (job) {
    job.status = 'failed';
    job.error = error;
    job.updatedAt = new Date();
  }
}

/**
 * Delete a job (for cleanup after client retrieves result)
 */
export function deleteJob(id: string): boolean {
  return jobs.delete(id);
}

/**
 * Clean up expired jobs
 */
function cleanupExpiredJobs(): void {
  const now = Date.now();
  for (const [id, job] of jobs.entries()) {
    if (now - job.createdAt.getTime() > JOB_EXPIRY_MS) {
      jobs.delete(id);
    }
  }
  for (const [id, job] of groceryPolishJobs.entries()) {
    if (now - job.createdAt.getTime() > JOB_EXPIRY_MS) {
      groceryPolishJobs.delete(id);
    }
  }
  for (const [id, job] of pantryCategorizeJobs.entries()) {
    if (now - job.createdAt.getTime() > JOB_EXPIRY_MS) {
      pantryCategorizeJobs.delete(id);
    }
  }
}

// ============================================================================
// Grocery Polish Jobs
// ============================================================================

/**
 * Create a new grocery polish job
 */
export function createGroceryPolishJob(): GroceryPolishJob {
  const job: GroceryPolishJob = {
    id: randomUUID(),
    status: 'pending',
    progress: null,
    result: null,
    error: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  groceryPolishJobs.set(job.id, job);
  cleanupExpiredJobs();
  return job;
}

/**
 * Get a grocery polish job by ID
 */
export function getGroceryPolishJob(id: string): GroceryPolishJob | undefined {
  return groceryPolishJobs.get(id);
}

/**
 * Start a grocery polish job
 */
export function startGroceryPolishJob(id: string): void {
  const job = groceryPolishJobs.get(id);
  if (job) {
    job.status = 'running';
    job.updatedAt = new Date();
  }
}

/**
 * Update grocery polish job progress
 */
export function updateGroceryPolishProgress(id: string, progress: GroceryPolishProgress): void {
  const job = groceryPolishJobs.get(id);
  if (job) {
    job.progress = progress;
    job.updatedAt = new Date();
  }
}

/**
 * Complete a grocery polish job with result
 */
export function completeGroceryPolishJob(id: string, result: GroceryPolishResponse): void {
  const job = groceryPolishJobs.get(id);
  if (job) {
    job.status = 'completed';
    job.result = result;
    job.progress = { phase: 'complete', currentBatch: 1, totalBatches: 1 };
    job.updatedAt = new Date();
  }
}

/**
 * Fail a grocery polish job with error
 */
export function failGroceryPolishJob(id: string, error: string): void {
  const job = groceryPolishJobs.get(id);
  if (job) {
    job.status = 'failed';
    job.error = error;
    job.updatedAt = new Date();
  }
}

/**
 * Delete a grocery polish job
 */
export function deleteGroceryPolishJob(id: string): boolean {
  return groceryPolishJobs.delete(id);
}

// ============================================================================
// Pantry Categorize Jobs
// ============================================================================

/**
 * Create a new pantry categorize job
 */
export function createPantryCategorizeJob(): PantryCategorizeJob {
  const job: PantryCategorizeJob = {
    id: randomUUID(),
    status: 'pending',
    progress: null,
    result: null,
    error: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  pantryCategorizeJobs.set(job.id, job);
  cleanupExpiredJobs();
  return job;
}

/**
 * Get a pantry categorize job by ID
 */
export function getPantryCategorizeJob(id: string): PantryCategorizeJob | undefined {
  return pantryCategorizeJobs.get(id);
}

/**
 * Start a pantry categorize job
 */
export function startPantryCategorizeJob(id: string): void {
  const job = pantryCategorizeJobs.get(id);
  if (job) {
    job.status = 'running';
    job.updatedAt = new Date();
  }
}

/**
 * Update pantry categorize job progress
 */
export function updatePantryCategorizeProgress(id: string, progress: PantryCategorizeProgress): void {
  const job = pantryCategorizeJobs.get(id);
  if (job) {
    job.progress = progress;
    job.updatedAt = new Date();
  }
}

/**
 * Complete a pantry categorize job with result
 */
export function completePantryCategorizeJob(id: string, result: PantryCategorizeResponse): void {
  const job = pantryCategorizeJobs.get(id);
  if (job) {
    job.status = 'completed';
    job.result = result;
    job.progress = { phase: 'complete', current: 1, total: 1 };
    job.updatedAt = new Date();
  }
}

/**
 * Fail a pantry categorize job with error
 */
export function failPantryCategorizeJob(id: string, error: string): void {
  const job = pantryCategorizeJobs.get(id);
  if (job) {
    job.status = 'failed';
    job.error = error;
    job.updatedAt = new Date();
  }
}

/**
 * Delete a pantry categorize job
 */
export function deletePantryCategorizeJob(id: string): boolean {
  return pantryCategorizeJobs.delete(id);
}
