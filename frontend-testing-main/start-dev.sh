#!/bin/bash

# Market Mono Development Startup Script
# This script handles the complete development workflow:
# 1. Creates backup branch
# 2. Merges from main (instead of rebase)
# 3. Updates dependencies
# 4. Starts backend and frontend services

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Project root directory
PROJECT_ROOT="/Users/joelbodker/Projects/frontend-testing"
BACKEND_DIR="$PROJECT_ROOT/backend"

echo -e "${BLUE}🚀 Starting Market Mono Development Environment${NC}"
echo "=================================================="

# Function to print status
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_debug() {
    echo -e "${PURPLE}🔍 DEBUG: $1${NC}"
}

# Function to handle git errors with detailed debugging
handle_git_error() {
    local step="$1"
    local error_code="$2"
    
    print_error "Git operation failed during: $step"
    echo ""
    print_debug "Git Status:"
    git status --porcelain
    echo ""
    print_debug "Git Log (last 3 commits):"
    git log --oneline -3
    echo ""
    print_debug "Git Branch Info:"
    git branch -v
    echo ""
    print_debug "Git Remote Info:"
    git remote -v
    echo ""
    
    case $step in
        "fetch")
            print_error "Failed to fetch from origin. Check your internet connection and remote URL."
            echo "Manual fix: git fetch origin"
            ;;
        "merge")
            print_error "Merge conflicts detected. You need to resolve them manually."
            echo ""
            echo "Conflicted files:"
            git diff --name-only --diff-filter=U
            echo ""
            echo "To resolve conflicts:"
            echo "1. Edit the conflicted files"
            echo "2. Remove conflict markers (<<<<<<< HEAD, =======, >>>>>>> origin/main)"
            echo "3. Run: git add <resolved-files>"
            echo "4. Run: git commit -m 'fix: resolve merge conflicts'"
            echo "5. Run: ./start-dev.sh again"
            echo ""
            echo "To abort merge: git merge --abort"
            ;;
        "push")
            print_error "Failed to push to remote. Check your permissions and remote URL."
            echo "Manual fix: git push origin feature/joel"
            ;;
    esac
    
    exit 1
}

# Check if we're in the right directory
if [ ! -f "$PROJECT_ROOT/package.json" ]; then
    print_error "Not in project root directory. Please run from: $PROJECT_ROOT"
    exit 1
fi

# Navigate to project root
cd "$PROJECT_ROOT"

# Step 1: Git Operations
echo -e "\n${BLUE}📋 Step 1: Git Operations${NC}"
echo "----------------------------"

# Check git status
if [ -n "$(git status --porcelain)" ]; then
    print_warning "Uncommitted changes detected. Committing them..."
    git add .
    git commit -m "feat: auto-commit before merge $(date '+%Y-%m-%d %H:%M:%S')" || {
        print_error "Failed to commit changes. Check for merge conflicts or staging issues."
        echo "Manual fix: git add . && git commit -m 'your message'"
        exit 1
    }
    print_status "Changes committed"
fi

# Backup branch creation removed per user request

# Fetch latest changes
echo "Fetching latest changes from origin..."
if ! git fetch origin; then
    handle_git_error "fetch" $?
fi
print_status "Latest changes fetched"

# Check what we're about to merge
echo "Checking for new commits on main..."
COMMITS_AHEAD=$(git rev-list --count HEAD..origin/main)
if [ $COMMITS_AHEAD -gt 0 ]; then
    print_warning "Found $COMMITS_AHEAD new commits on main to merge"
    echo "New commits:"
    git log --oneline HEAD..origin/main
    echo ""
else
    print_status "No new commits on main to merge"
fi

# Merge from main (instead of rebase)
echo "Merging latest changes from main branch..."
if ! git merge origin/main --no-edit; then
    handle_git_error "merge" $?
fi
print_status "Successfully merged from main"

# Push updated branch
echo "Updating remote branch..."
if ! git push origin feature/joel; then
    handle_git_error "push" $?
fi
print_status "Remote branch updated"

# Step 2: Dependency Updates
echo -e "\n${BLUE}📦 Step 2: Dependency Updates${NC}"
echo "--------------------------------"

# Update frontend dependencies
echo "Updating frontend dependencies..."
cd "$PROJECT_ROOT"
if ! npm install; then
    print_error "Failed to install frontend dependencies"
    echo "Manual fix: cd $PROJECT_ROOT && npm install"
    exit 1
fi
print_status "Frontend dependencies updated"

# Update backend dependencies
echo "Updating backend dependencies..."
cd "$BACKEND_DIR"
if ! npm install; then
    print_error "Failed to install backend dependencies"
    echo "Manual fix: cd $BACKEND_DIR && npm install"
    exit 1
fi
print_status "Backend dependencies updated"

# Step 3: Start Services
echo -e "\n${BLUE}🖥️  Step 3: Starting Services${NC}"
echo "--------------------------------"

# Kill any existing processes on our ports
echo "Cleaning up existing processes..."
lsof -ti:3001 | xargs kill -9 2>/dev/null || true
lsof -ti:5173 | xargs kill -9 2>/dev/null || true
print_status "Existing processes cleaned up"

# Start ClickHouse if not running
echo "Starting ClickHouse database..."
if ! curl -s http://localhost:8123/ping > /dev/null 2>&1; then
    echo "ClickHouse not running, starting it..."
    clickhouse server > /dev/null 2>&1 &
    CLICKHOUSE_PID=$!
    echo $CLICKHOUSE_PID > "$PROJECT_ROOT/clickhouse.pid"
    
    # Wait for ClickHouse to start
    echo "Waiting for ClickHouse to start..."
    for i in {1..30}; do
        if curl -s http://localhost:8123/ping > /dev/null 2>&1; then
            print_status "ClickHouse started successfully (PID: $CLICKHOUSE_PID)"
            break
        fi
        sleep 1
    done
    
    if ! curl -s http://localhost:8123/ping > /dev/null 2>&1; then
        print_error "ClickHouse failed to start after 30 seconds"
        echo "You may need to start it manually: clickhouse server"
        echo "Check ClickHouse logs for errors"
    fi
else
    print_status "ClickHouse is already running"
    CLICKHOUSE_PID=$(lsof -ti:8123 | head -1)
fi

# Start backend in background
echo "Starting backend server..."
cd "$BACKEND_DIR"
npm run dev > "$PROJECT_ROOT/backend.log" 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > "$PROJECT_ROOT/backend.pid"
print_status "Backend started (PID: $BACKEND_PID)"

# Wait a moment for backend to start
sleep 3

# Check if backend started successfully
if ! curl -s http://localhost:3001/health > /dev/null 2>&1; then
    print_warning "Backend may not have started properly. Check backend.log for errors."
fi

# Start frontend in background
echo "Starting frontend server with HMR enabled..."
cd "$PROJECT_ROOT"
# Enable HMR debugging and ensure proper WebSocket connection
VITE_HMR_PORT=5173 VITE_HMR_HOST=localhost npm run dev > "$PROJECT_ROOT/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > "$PROJECT_ROOT/frontend.pid"
print_status "Frontend started with HMR (PID: $FRONTEND_PID)"

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 5

# Check HMR WebSocket connection
echo "Checking HMR WebSocket connection..."
if curl -s http://localhost:5173 > /dev/null 2>&1; then
    print_status "Frontend server is responding"
    print_status "HMR WebSocket should be available at ws://localhost:5173"
    print_warning "If HMR isn't working, try refreshing your browser (Cmd+R)"
else
    print_warning "Frontend server may not be responding properly"
fi

# Check if services are running
BACKEND_RUNNING=false
FRONTEND_RUNNING=false

if ps -p $BACKEND_PID > /dev/null 2>&1; then
    BACKEND_RUNNING=true
fi

if ps -p $FRONTEND_PID > /dev/null 2>&1; then
    FRONTEND_RUNNING=true
fi

if [ "$BACKEND_RUNNING" = true ] && [ "$FRONTEND_RUNNING" = true ]; then
    print_status "Both services are running successfully!"
else
    print_warning "One or more services may have issues. Check logs:"
    echo "Backend log: $PROJECT_ROOT/backend.log"
    echo "Frontend log: $PROJECT_ROOT/frontend.log"
fi

# Step 4: Display Status
echo -e "\n${BLUE}📊 Step 4: Development Environment Status${NC}"
echo "=============================================="

echo -e "${GREEN}✅ Git Status:${NC}"
echo "  - Branch: $(git branch --show-current)"
echo "  - Latest main: $(git log --oneline -1 origin/main)"
echo "  - Commits ahead: $(git rev-list --count origin/main..HEAD)"

echo -e "\n${GREEN}✅ Services:${NC}"
echo "  - ClickHouse: http://localhost:8123 (PID: $CLICKHOUSE_PID)"
echo "  - Backend: http://localhost:3001 (PID: $BACKEND_PID)"
echo "  - Frontend: http://localhost:5173 (PID: $FRONTEND_PID)"

echo -e "\n${GREEN}✅ Logs:${NC}"
echo "  - Backend: $PROJECT_ROOT/backend.log"
echo "  - Frontend: $PROJECT_ROOT/frontend.log"

echo -e "\n${GREEN}✅ Process IDs:${NC}"
echo "  - ClickHouse PID: $CLICKHOUSE_PID (saved to clickhouse.pid)"
echo "  - Backend PID: $BACKEND_PID (saved to backend.pid)"
echo "  - Frontend PID: $FRONTEND_PID (saved to frontend.pid)"

echo -e "\n${GREEN}✅ HMR (Hot Module Reload):${NC}"
echo "  - WebSocket: ws://localhost:5173"
echo "  - If HMR isn't working:"
echo "    1. Refresh browser (Cmd+R)"
echo "    2. Check browser console for errors"
echo "    3. Verify WebSocket connection in Network tab"
echo "    4. Try hard refresh (Cmd+Shift+R)"

echo -e "\n${BLUE}🎉 Development environment is ready!${NC}"
echo "=================================================="
echo "ClickHouse: http://localhost:8123"
echo "Frontend: http://localhost:5173"
echo "Backend API: http://localhost:3001"
echo ""
echo "To stop everything, run: ./stop-dev.sh"
echo "=================================================="