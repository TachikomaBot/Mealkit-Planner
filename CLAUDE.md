# Mealkit Planner - Claude Code Instructions

## Beans Issue Tracker

This project uses [beans](https://github.com/hmans/beans) for issue tracking.

**Before starting any work, run:**
```bash
beans prime
```

This outputs context about open issues, priorities, and project state. Heed its output when planning work.

## Useful beans commands

- `beans list` - List all open issues
- `beans show <id>` - Show details of a specific issue
- `beans create "title" -t <type> -p <priority>` - Create new issue
- `beans update <id> --status in-progress` - Mark issue as in progress
- `beans update <id> --status completed` - Mark issue as complete
- `beans tui` - Interactive terminal UI

## Project Overview

Android meal planning app built with Jetpack Compose. Key features:
- AI-powered recipe generation (Gemini)
- Pantry tracking with smart stock levels
- Shopping list management
- Recipe-to-pantry deduction flow

## Gemini Model

**Always use `gemini-3-flash-preview` when writing new API calls to Gemini.** This is the model used for all AI features including meal plan generation, recipe customization, ingredient substitution, and pantry categorization.
