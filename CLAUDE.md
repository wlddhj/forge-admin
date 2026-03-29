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
| Frontend Dev | 3002 | `apps/forge-web` |
| Backend API | 8180 | `apps/forge-server` |
| Context Path | - | `/api` |
| API Docs | 8180 | `/api/doc.html` |

**Database:** MySQL `forge_admin` on localhost:3306
**Redis:** localhost:6379 (no password)
**Java:** 21 | **Node:** 22.9.0 | **pnpm:** 8.15.4

## Development Commands

### Frontend (from `apps/forge-web`)
```bash
pnpm install    # Install dependencies
pnpm dev        # Start dev server (port 3002)
pnpm build      # Build for production (includes type check)
pnpm preview    # Preview production build
pnpm lint       # Run ESLint
```

### Backend (from `apps/forge-server`)
```bash
mvn spring-boot:run              # Start dev server
mvn clean compile                # Compile only
mvn clean package -DskipTests    # Build JAR (skip tests)
mvn test                         # Run all tests
mvn test -Dtest=ClassName       # Run single test class
mvn test -Dtest=ClassName#methodName  # Run single test method
```

## Architecture

### Backend Structure (`apps/forge-server/src/main/java/com/forge/admin/`)
```
common/
├── annotation/    # Custom annotations (@OperationLog, @DataPermission, @RateLimiter)
├── aspect/        # AOP aspects (logging, rate limiting, data scope)
├── config/        # Spring configurations (Security, Redis, JWT, MyBatis Plus)
├── enumeration/   # Enums (DataScope types)
├── exception/     # Global exception handling
├── json/          # Jackson serializers/deserializers
├── permission/    # Data permission rules and interceptors
├── response/      # Unified API response wrapper (Result<T>, PageResult<T>)
└── utils/         # Utility classes (SecurityUtils, ExcelUtils, etc.)

modules/
├── auth/          # Authentication (login, token refresh, JWT filter)
│   ├── controller/
│   ├── dto/
│   ├── security/  # JWT token provider, filters
│   └── service/
└── system/        # System management
    ├── controller/
    ├── dto/
    ├── entity/
    ├── mapper/
    └── service/
```

### Frontend Structure (`apps/forge-web/src/`)
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
Location: `apps/forge-server/src/main/resources/db/migration/`
Naming: `V{YYYYMMDD}{seq}__{description}.sql` (e.g., `V2026030501__file_config.sql`)

### Data Permission System
The system supports department-level data isolation through `@DataPermission` annotation:
- Type 1: All data access
- Type 2: Custom data permission
- Type 3: Own department only
- Type 4: Own department and sub-departments
- Type 5: Own data only

## Git Commit Convention

**Format:** `<type>(<scope>): <subject>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `revert`

**IMPORTANT:** Do NOT add `Co-Authored-By` in commit messages.

## Key Files

| Purpose | Path |
|---------|------|
| Backend Config | `apps/forge-server/src/main/resources/application.yml` |
| API Definitions | `apps/forge-server/src/main/java/com/forge/admin/modules/*/controller/` |
| Frontend Request Utils | `apps/forge-web/src/utils/request.ts` |
| Pinia Stores | `apps/forge-web/src/stores/` |
| Vue Router | `apps/forge-web/src/router/` |

## Pre-existing Rules

Refer to `.claude/PROJECT.md` for detailed coding templates, naming conventions, and API patterns.
