# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

forge-admin is an enterprise-level admin management system with RBAC (Role-Based Access Control). It's a monorepo with separate frontend and backend applications.

**Tech Stack:**
- Frontend: Vue 3.4 + TypeScript + Element Plus + Pinia + Vite
- Backend: Spring Boot 3.2.0 + MyBatis Plus + MySQL + Redis
- Auth: JWT Token
- API Docs: Knife4j (Swagger UI at `/api/doc.html`)

## Key Configuration

| Service | Port | Path |
|---------|------|------|
| Frontend Dev | 3002 | `apps/frontend` |
| Backend API | 8180 | `apps/backend` |
| Context Path | - | `/api` |
| API Docs | 8180 | `/api/doc.html` |

**Database:** MySQL `forge_admin` on localhost:3306
**Redis:** localhost:6379 (no password)
**Java:** 21 | **Node:** 22.9.0 | **pnpm:** 8.15.4

## Development Commands

### Frontend (from `apps/frontend`)
```bash
pnpm install    # Install dependencies
pnpm dev        # Start dev server (port 3002)
pnpm build      # Build for production (includes type check)
pnpm lint       # Run ESLint
```

### Backend (from `apps/backend`)
```bash
mvn spring-boot:run              # Start dev server
mvn clean compile                # Compile only
mvn clean package -DskipTests    # Build JAR (skip tests)
mvn test                         # Run tests
```

## Architecture

### Backend Structure (`apps/backend/src/main/java/com/standadmin/`)
```
common/
├── annotation/    # Custom annotations (e.g., @OperationLog)
├── aspect/        # AOP aspects
├── config/        # Spring configurations (Security, Redis, JWT, etc.)
├── exception/     # Global exception handling
├── json/          # Jackson serializers/deserializers
├── permission/    # Data permission interceptors
├── response/      # Unified API response wrapper (Result<T>)
└── utils/         # Utility classes

modules/
├── auth/          # Authentication (login, token refresh)
├── system/        # System management (user, role, menu, dept, dict, config)
└── quartz/        # Scheduled tasks
```

### Frontend Structure (`apps/frontend/src/`)
```
api/           # API request modules (organized by feature)
components/    # Shared components (auto-registered via unplugin)
composables/   # Vue composables (hooks)
layouts/       # Layout components
router/        # Vue Router configuration
stores/        # Pinia stores
styles/        # Global styles
types/         # TypeScript type definitions
utils/         # Utilities (request, formatters, etc.)
views/         # Page components
```

## Important Patterns

### API Response Format
All API responses follow this structure:
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1709635800000
}
```

### Permission Format
- Authority: `system:user:list`, `system:user:add`, `system:user:edit`, `system:user:delete`
- Usage in controller: `@PreAuthorize("hasAuthority('system:user:list')")`

### Database Tables
- Prefix: `sys_` (e.g., `sys_user`, `sys_role`, `sys_menu`)
- Common fields: `id`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `status`, `remark`

### Database Migrations
Location: `apps/backend/src/main/resources/db/migration/`
Naming: `V{YYYYMMDD}{seq}__{description}.sql` (e.g., `V2026030501__file_config.sql`)

## Git Commit Convention

**Format:** `<type>(<scope>): <subject>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `revert`

**IMPORTANT:** Do NOT add `Co-Authored-By` in commit messages.

## Pre-existing Rules

Refer to `.claude/PROJECT.md` for detailed coding templates, naming conventions, and API patterns.
