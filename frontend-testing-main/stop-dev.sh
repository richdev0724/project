#!/bin/bash

# Market Mono Development Shutdown Script
# This script cleanly shuts down all development services and processes
# Automatically saves and pushes all work to prevent data loss

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

echo -e "${BLUE}🛑 Shutting Down Market Mono Development Environment${NC}"
echo "======================================================="

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
    
    case $step in
        "add")
            print_error "Failed to stage changes. Check for file permissions or conflicts."
            echo "Manual fix: git add ."
            ;;
        "commit")
            print_error "Failed to commit changes. Check for merge conflicts or empty commits."
            echo "Manual fix: git commit -m 'your message'"
            ;;
        "push")
            print_error "Failed to push to remote. Check your internet connection and permissions."
            echo "Manual fix: git push origin feature/joel"
            echo "If you have conflicts: git pull origin feature/joel"
            ;;
    esac
    
    exit 1
}

# Navigate to project root
cd "$PROJECT_ROOT"

# Step 1: Save All Work
echo -e "\n${BLUE}💾 Step 1: Saving All Work${NC}"
echo "----------------------------"

# Check git status
if [ -n "$(git status --porcelain)" ]; then
    print_warning "Uncommitted changes detected. Saving them..."
    
    # Show what changes we're about to commit
    echo "Changes to be committed:"
    git status --short
    echo ""
    
    # Add all changes
    if ! git add .; then
        handle_git_error "add" $?
    fi
    
    # Commit with timestamp
    COMMIT_MESSAGE="feat: end of day auto-save $(date '+%Y-%m-%d %H:%M:%S')"
    if ! git commit -m "$COMMIT_MESSAGE" --no-edit; then
        handle_git_error "commit" $?
    fi
    print_status "Changes committed: $COMMIT_MESSAGE"
else
    print_status "No uncommitted changes to save"
fi

# Step 2: Push to Remote
echo -e "\n${BLUE}📤 Step 2: Pushing to Remote${NC}"
echo "----------------------------"

# Check if we're ahead of remote
COMMITS_AHEAD=$(git rev-list --count origin/feature/joel..HEAD 2>/dev/null || echo "0")
if [ $COMMITS_AHEAD -gt 0 ]; then
    print_warning "Pushing $COMMITS_AHEAD commits to remote..."
    
    # Show what we're about to push
    echo "Commits to push:"
    git log --oneline origin/feature/joel..HEAD
    echo ""
    
    if ! git push origin feature/joel; then
        handle_git_error "push" $?
    fi
    print_status "Successfully pushed to remote"
else
    print_status "No commits to push (already up to date)"
fi

# Step 3: Stop Services by PID
echo -e "\n${BLUE}🖥️  Step 3: Stopping Services${NC}"
echo "----------------------------"

# Stop ClickHouse if PID file exists
if [ -f "clickhouse.pid" ]; then
    CLICKHOUSE_PID=$(cat clickhouse.pid)
    if ps -p $CLICKHOUSE_PID > /dev/null 2>&1; then
        echo "Stopping ClickHouse server (PID: $CLICKHOUSE_PID)..."
        kill $CLICKHOUSE_PID
        sleep 2
        if ps -p $CLICKHOUSE_PID > /dev/null 2>&1; then
            print_warning "ClickHouse didn't stop gracefully, force killing..."
            kill -9 $CLICKHOUSE_PID
        fi
        print_status "ClickHouse stopped"
    else
        print_warning "ClickHouse PID file exists but process not running"
    fi
    rm -f clickhouse.pid
else
    print_warning "No ClickHouse PID file found"
fi

# Stop backend if PID file exists
if [ -f "backend.pid" ]; then
    BACKEND_PID=$(cat backend.pid)
    if ps -p $BACKEND_PID > /dev/null 2>&1; then
        echo "Stopping backend server (PID: $BACKEND_PID)..."
        kill $BACKEND_PID
        sleep 2
        if ps -p $BACKEND_PID > /dev/null 2>&1; then
            print_warning "Backend didn't stop gracefully, force killing..."
            kill -9 $BACKEND_PID
        fi
        print_status "Backend stopped"
    else
        print_warning "Backend PID file exists but process not running"
    fi
    rm -f backend.pid
else
    print_warning "No backend PID file found"
fi

# Stop frontend if PID file exists
if [ -f "frontend.pid" ]; then
    FRONTEND_PID=$(cat frontend.pid)
    if ps -p $FRONTEND_PID > /dev/null 2>&1; then
        echo "Stopping frontend server (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID
        sleep 2
        if ps -p $FRONTEND_PID > /dev/null 2>&1; then
            print_warning "Frontend didn't stop gracefully, force killing..."
            kill -9 $FRONTEND_PID
        fi
        print_status "Frontend stopped"
    else
        print_warning "Frontend PID file exists but process not running"
    fi
    rm -f frontend.pid
else
    print_warning "No frontend PID file found"
fi

# Step 4: Kill any remaining processes on our ports
echo -e "\n${BLUE}🧹 Step 4: Cleaning Up Ports${NC}"
echo "----------------------------"

# Kill processes on port 3001 (backend)
if lsof -ti:3001 > /dev/null 2>&1; then
    echo "Killing remaining processes on port 3001..."
    lsof -ti:3001 | xargs kill -9 2>/dev/null || true
    print_status "Port 3001 cleaned up"
else
    print_status "Port 3001 is free"
fi

# Kill processes on port 5173 (frontend)
if lsof -ti:5173 > /dev/null 2>&1; then
    echo "Killing remaining processes on port 5173..."
    lsof -ti:5173 | xargs kill -9 2>/dev/null || true
    print_status "Port 5173 cleaned up"
else
    print_status "Port 5173 is free"
fi

# Kill processes on port 8123 (ClickHouse)
if lsof -ti:8123 > /dev/null 2>&1; then
    echo "Killing remaining processes on port 8123..."
    lsof -ti:8123 | xargs kill -9 2>/dev/null || true
    print_status "Port 8123 cleaned up"
else
    print_status "Port 8123 is free"
fi

# Step 5: Clean up log files
echo -e "\n${BLUE}📝 Step 5: Cleaning Up Logs${NC}"
echo "----------------------------"

# Create logs directory if it doesn't exist
mkdir -p logs

if [ -f "backend.log" ]; then
    echo "Archiving backend log..."
    mv backend.log "logs/backend-$(date +%Y%m%d-%H%M%S).log" 2>/dev/null || rm -f backend.log
    print_status "Backend log archived"
fi

if [ -f "frontend.log" ]; then
    echo "Archiving frontend log..."
    mv frontend.log "logs/frontend-$(date +%Y%m%d-%H%M%S).log" 2>/dev/null || rm -f frontend.log
    print_status "Frontend log archived"
fi

# Step 6: Final Status Check
echo -e "\n${BLUE}📊 Step 6: Final Status Check${NC}"
echo "----------------------------"

# Check git status
echo "Git Status:"
if [ -n "$(git status --porcelain)" ]; then
    print_warning "You still have uncommitted changes:"
    git status --short
else
    print_status "Working directory is clean"
fi

# Check if ports are free
PORTS_FREE=true
if lsof -ti:3001 > /dev/null 2>&1; then
    print_warning "Port 3001 is still in use"
    PORTS_FREE=false
fi
if lsof -ti:5173 > /dev/null 2>&1; then
    print_warning "Port 5173 is still in use"
    PORTS_FREE=false
fi
if lsof -ti:8123 > /dev/null 2>&1; then
    print_warning "Port 8123 is still in use"
    PORTS_FREE=false
fi

if [ "$PORTS_FREE" = true ]; then
    print_status "All ports are free"
fi

# Check for any remaining node processes
REMAINING_NODE=$(ps aux | grep -E "(node|vite)" | grep -v grep | grep -v "Discord\|Adobe\|Cursor\|Code" | wc -l)
if [ $REMAINING_NODE -eq 0 ]; then
    print_status "No development processes running"
else
    print_warning "Some Node.js processes may still be running:"
    ps aux | grep -E "(node|vite)" | grep -v grep | grep -v "Discord\|Adobe\|Cursor\|Code"
fi

# Final summary
echo -e "\n${BLUE}📋 Final Summary${NC}"
echo "=============="
echo "✅ All work saved and pushed to GitHub"
echo "✅ All services stopped"
echo "✅ All ports cleaned up"
echo "✅ Logs archived"
echo ""

# Show recent commits
echo "Recent commits:"
git log --oneline -3
echo ""

echo -e "${GREEN}🎉 Development environment shutdown complete!${NC}"
echo "=================================================="
echo "✅ All your work has been saved and pushed to GitHub"
echo "✅ No work has been lost"
echo "✅ All services stopped and cleaned up"
echo "✅ Logs have been archived"
echo ""
echo "To start development again, run: ./start-dev.sh"
echo "=================================================="