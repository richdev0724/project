# Git Operations Guide

## Overview
This guide covers common Git operations and troubleshooting scenarios for the Market Mono frontend-testing project.

## Branch Management

### Current Branch Structure
- `main` - Production-ready code
- `feature/joel` - Active development branch
- `feature/joel-backup-*` - Backup branches created before major operations

### Safe Branch Operations
```bash
# Create backup before major operations
git branch feature/joel-backup-$(date +%Y%m%d-%H%M%S)

# Check all branches (local and remote)
git branch -a

# Switch between branches
git checkout <branch-name>
```

## Merging and Rebasing

### When to Use Each Strategy

#### Rebase (Recommended for Feature Branches)
- **Use when**: Working on feature branches
- **Benefits**: Clean, linear history
- **Command**: `git pull --rebase origin main`

#### Merge
- **Use when**: Combining completed features
- **Benefits**: Preserves branch history
- **Command**: `git pull --no-rebase origin main`

#### Fast-Forward Only
- **Use when**: No local changes exist
- **Command**: `git pull --ff-only origin main`

### Resolving Merge Conflicts

#### Common Conflict Files
1. `docker-compose.yml` - Service configurations
2. `src/api/index.ts` - API service exports
3. `backend/src/index.js` - Route registrations

#### Conflict Resolution Process
```bash
# 1. Check status
git status

# 2. Edit conflicted files
# Look for <<<<<<< HEAD, =======, >>>>>>> markers
# Choose which changes to keep

# 3. Mark as resolved
git add <resolved-file>

# 4. Continue operation
git rebase --continue  # for rebase
# OR
git commit  # for merge
```

#### Example Conflict Resolution
```yaml
# Before (conflicted)
<<<<<<< HEAD
const earningsRouter = require('./routes/earnings');
=======
const timeframeDataRouter = require('./routes/timeframeData');
>>>>>>> commit-hash

# After (resolved)
const earningsRouter = require('./routes/earnings');
const timeframeDataRouter = require('./routes/timeframeData');
```

## Recovery Operations

### If Rebase Goes Wrong
```bash
# Option 1: Abort and return to original state
git rebase --abort

# Option 2: Restore from backup branch
git checkout feature/joel-backup-YYYYMMDD-HHMMSS
git branch -D feature/joel
git checkout -b feature/joel

# Option 3: Reset to remote branch
git reset --hard origin/feature/joel
```

### If Merge Goes Wrong
```bash
# Abort merge
git merge --abort

# Reset to previous state
git reset --hard HEAD~1
```

## Common Scenarios

### Scenario 1: Divergent Branches Error
**Error**: `fatal: Need to specify how to reconcile divergent branches`

**Solution**:
```bash
# Choose strategy based on situation
git pull --rebase origin main    # For feature branches
git pull --no-rebase origin main # For merge strategy
git pull --ff-only origin main   # For fast-forward only
```

### Scenario 2: Terminal Editor Issues During Rebase
**Error**: `error: Terminal is dumb, but EDITOR unset`

**Solution**:
```bash
# Set editor and continue
EDITOR=true git rebase --continue

# Or set globally
git config --global core.editor "code --wait"
```

### Scenario 3: Branch Diverged After Rebase
**Status**: `Your branch and 'origin/feature/joel' have diverged`

**Solution**:
```bash
# Force push to update remote (use with caution)
git push --force-with-lease origin feature/joel

# Or create new branch if unsure
git checkout -b feature/joel-rebased
git push origin feature/joel-rebased
```

## Safety Best Practices

### Before Major Operations
1. **Create backup branch**: `git branch backup-$(date +%Y%m%d-%H%M%S)`
2. **Check status**: `git status`
3. **Verify remote**: `git fetch origin`
4. **Review changes**: `git log --oneline -10`

### During Operations
1. **Resolve conflicts carefully** - understand what each side changed
2. **Test after resolution** - ensure code still works
3. **Commit frequently** - smaller commits are easier to debug

### After Operations
1. **Verify branch state**: `git status`
2. **Test functionality**: Run the application
3. **Update remote if needed**: `git push --force-with-lease`

## Project-Specific Notes

### Protected Files
- `main` branch is protected on remote
- Local operations cannot affect remote `main`
- Always work on feature branches

### Common Conflict Areas
- Docker configurations (`docker-compose.yml`)
- API service exports (`src/api/index.ts`)
- Backend route registrations (`backend/src/index.js`)
- Package dependencies (`package.json`, `package-lock.json`)

### Environment Considerations
- Development: Use `feature/joel` branch
- Testing: Create `feature/joel-test` branches
- Production: Only `main` branch

## Quick Reference Commands

```bash
# Status and information
git status
git log --oneline -10
git branch -a

# Safe operations
git fetch origin
git checkout -b backup-$(date +%Y%m%d-%H%M%S)

# Merge/Rebase operations
git pull --rebase origin main
git pull --no-rebase origin main

# Recovery
git rebase --abort
git reset --hard HEAD~1
git checkout backup-branch-name

# Push operations
git push origin feature/joel
git push --force-with-lease origin feature/joel
```

## Emergency Contacts
- Backup branches: `feature/joel-backup-*`
- Remote branches: `origin/feature/joel`
- Main branch: `origin/main` (protected)

---
*Last updated: $(date)*
*Created during rebase operation resolving divergent branches*

