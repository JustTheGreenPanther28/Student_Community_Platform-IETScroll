# IET Scroll — Full-Stack Platform

A full-stack web platform for the **IET Scroll** community app for IET DAVV students — a Spring Boot REST API backend paired with a server-rendered vanilla HTML/CSS/JS frontend (served as static resources from the same app). Supports lost & found item management, team formation, AI-powered resume review, OTP-based authentication, and image moderation.

---

## Tech Stack

| Layer                  | Technology                           |
|------------------------|--------------------------------------|
| Backend Framework       | Spring Boot                          |
| Frontend                | HTML5, CSS3, vanilla JavaScript (fetch-based SPA-style dashboard) |
| Security               | Spring Security (JWT / Basic Auth) |
| ORM                    | Spring Data JPA                      |
| Database               | Postgres                                |
| Image Storage          | Cloudinary                           |
| Image Moderation     | SightEngine API                     |
| AI / LLM              | Spring AI (Llama for resumes, Mistral for moderation) |
| Email                  | JavaMailSender (Brevo SMTP)          |
| PDF Parsing            | Apache Tika                          |
| API Documentation      | Swagger / OpenAPI 3                  |

---

## Frontend Pages
`src/main/resources/static`

| Page | File | Purpose |
|------|------|---------|
| Landing | `index.html` | Public marketing/intro page for the platform |
| Login | `login.html` + `login.js` | Authenticates via `/login`, stores JWT in `localStorage` |
| Register | `register.html` + `register.js` | Registration + OTP verification flow |
| Dashboard | `dashboard.html` + `dashboard.js` | Main authenticated app — sidebar-navigated views for Profile, Lost Items, Found Items, Team Finder, and Resume Checker, all driven by a shared `api()` fetch wrapper that attaches the JWT, handles 401s (auto-logout), and surfaces backend error messages via toasts |
| Safety & Trust | `safety.html` | Public page on platform safety/moderation policies |

The dashboard is a single-page-style client: one HTML shell with JS-driven view switching (`show('lost' | 'found' | 'team' | 'resume' | 'profile', ...)`), backed entirely by the REST API below.

---

## Features

- **User Registration & OTP Verification**  
  Register with institute email (@ietdavv.edu.in). An OTP is sent via email and must be verified within 10 minutes. OTP requests are rate-limited (60s cooldown between resends), each new OTP invalidates any prior one, and a verified OTP is deleted immediately after use to prevent replay.

- **Lost & Found**  
  Report lost/found items with image uploads. Images are moderated via SightEngine before storage in Cloudinary.

- **Team Finder**  
  Create, browse, and join teams. Team purposes are moderated by the Mistral LLM before saving.

- **Resume Checker**  
  Upload resumes (PDF/DOCX) to receive AI-generated feedback, including scores, missing keywords, and suggestions, via the Llama LLM. Uploaded files are validated by sniffing actual file bytes (not just the client-supplied content type), capped at 5MB, and resume text is delimited before being sent to the LLM to reduce prompt-injection risk.

- **Paginated Feeds**  
  View lost items, found items, and teams with pagination, sorted by latest.

- **Consistent Error Responses**  
  All API errors return the correct HTTP status (404/409/422/429/etc.) and a clear message via a centralized exception handler.
  
---

## API Endpoints

### User
`/api/v1/user`

| Method | Endpoint             | Description                                    |
|---------|----------------------|------------------------------------------------|
| POST    | `/register`          | Register with institute email; triggers OTP    |
| GET     | `/`                  | Get current user's profile                     |
| PATCH   | `/username/{newUsername}` | Update username                        |
| PATCH   | `/fullname/{fullname}`    | Update full name                          |

### OTP
`/api/v1/otp`

| Method | Endpoint   | Description                       |
|---------|------------|-----------------------------------|
| POST    | `/verify` | Verify OTP and activate account   |

### Lost Items
`/api/v1/lost-item`

| Method | Endpoint | Description                                               |
|---------|----------|-----------------------------------------------------------|
| POST    | `/`      | Report a lost item (with image and details)               |
| GET     | `/me`    | Get current user's active lost items                     |
| GET     | `/`      | Get all open lost items (paginated)                        |
| PATCH   | `/close` | Close a lost item request                                |

### Found Items
`/api/v1/found-item`

| Method | Endpoint | Description                                               |
|---------|----------|-----------------------------------------------------------|
| POST    | `/`      | Report a found item (with image and details)               |
| GET     | `/me`    | Get current user's active found items                     |
| GET     | `/`      | Get all pending found items (paginated)                   |
| PATCH   | `/close` | Close a found item request                                |

### Teams
`/api/v1/team`

| Method | Endpoint     | Description                                              |
|---------|--------------|----------------------------------------------------------|
| POST    | `/`          | Create a team (AI-moderated purpose)                     |
| GET     | `/`          | Browse all public active teams (paginated)              |
| GET     | `/me`        | Get authenticated user's active team                     |
| PATCH   | `/close`     | Close your team                                         |
| PATCH   | `/team-size` | Update max team size (min: 3)                             |

### Team Join Requests
`/api/v1/request-team`

| Method | Endpoint                       | Description                                       |
|---------|--------------------------------|---------------------------------------------------|
| POST    | `/`                            | Submit a join request with a message              |
| GET     | `/requests`                    | View pending join requests (team owner)           |
| GET     | `/team-members`                | View accepted team members                        |
| PATCH   | `/accept/{applicantEmail}`     | Accept a join request                             |
| PATCH   | `/reject/{applicantEmail}`     | Reject a join request                             |
| PATCH   | `/remove/{applicantEmail}`     | Remove a team member                              |
| GET     | `/my-application`              | View all your submitted applications              |

### Resume Checker
`/api/v1/ietscroll/resume`

| Method | Endpoint | Description                                              |
|---------|----------|----------------------------------------------------------|
| POST    | `/quality` | Upload resume (PDF/DOCX) for AI-generated quality report |

---

## Key Business Rules

- Only `@ietdavv.edu.in` email addresses can register (plus configurable admin email).
- OTP expires after 10 minutes; account must be verified before login.
- Users can have at most 2 active lost-item requests and 3 active found-item requests.
- Users can create only 1 active team at a time.
- All uploaded images pass through SightEngine moderation (nudity, violence, weapons, drugs, etc.).
- Team purpose is validated by Mistral LLM before saving.
- Resume analysis uses Llama via Spring AI, with Apache Tika for text extraction.
- Uploaded files are capped at 5MB (10MB per multipart request).
- Frontend origins allowed to call the API are restricted via `CORS_ALLOWED_ORIGINS` (no wildcard).

---

## Known Issues & Challenges

### Fixed
- **Global exception handler was catching the wrong `ApiException`.** It imported `com.cloudinary.api.exceptions.ApiException` instead of the app's own `com.ietscroll.exception.ApiException`, so custom exceptions (`ResourceNotFoundException`, `LimitExceededException`, `DuplicateResourceException`, `ContentModerationException`, `InappropriateImageException`) were never actually caught — they fell through to a generic 500 instead of returning their intended status and message. Fixed: correct import, and the handler now uses `exception.getStatus()` instead of a hardcoded 400.
- **Resume upload content-type check trusted the client-supplied header**, which is trivially spoofable. Now the actual file bytes are sniffed via Tika to determine the real type.
- **No cap on uploaded file size**, and no explicit multipart limits configured. Added a 5MB per-file / 10MB per-request cap.
- **Resume text was passed to the LLM with no separation from instructions**, a prompt-injection risk if a resume contained crafted text. Extracted text is now wrapped in explicit delimiters with an instruction not to follow anything inside them.
- **OTPs had no resend cooldown and were never invalidated** when a new one was requested — a user (or attacker) could spam OTP requests, and stale unexpired OTPs lingered validly alongside newer ones. Added a 60s resend cooldown, invalidation of prior OTPs on resend, and deletion of the OTP row immediately after successful verification (prevents replay).
- **CORS allowed all origins (`*`) with credentials enabled.** Now restricted to an explicit allowlist via `CORS_ALLOWED_ORIGINS`.
- **Expired and invalid/tampered JWTs returned an identical bare 401**, giving the client no way to distinguish "please log in again" from "this token was tampered with." Now returns distinct error bodies (`token_expired` vs `token_invalid`).

### Open / Planned
- **OTP store is Postgres-backed, not Redis.** Every OTP generate/verify round-trips the DB and relies on a manual expiry query rather than native TTL expiry. Planned: move OTPs to Redis.
- **No brute-force lockout on OTP verification attempts** (e.g. max 5 wrong guesses before invalidation). Requires an `attempts` column on the OTP record — blocked on a DB migration since `spring.jpa.hibernate.ddl-auto=validate` won't auto-apply schema changes.
- **No virus/malware scanning on uploaded files.** Resumes (PDF/DOCX) and lost & found images are parsed/stored without an antivirus pass (e.g. ClamAV), leaving a gap against malicious documents or zip-bomb-style DOCX payloads.
- **JWT has no revocation or refresh-token flow.** A token is valid for its full 24h lifetime with no way to invalidate it early (logout, password change, ban). Planned alongside the Redis migration, using it as a token-blacklist store.
- **No automated tests** for services, security filters, or business rules (active-item caps, OTP flow, team join limits).
- **No timeouts or circuit breakers on outbound calls** to the LLM providers (Mistral/Llama via NVIDIA), Cloudinary, SightEngine, or Brevo — a slow/unavailable third party can stall requests indefinitely.
- **No structured logging or request correlation IDs**, making it harder to trace a single request across the LLM, image moderation, and email steps it may touch.
- **`Role.ADMIN` and the `/api/v1/admin/**` route are wired into security config but no admin controller exists yet.**
- **No API-level rate limiting** beyond the OTP-specific cooldown above (e.g. no throttling on the resume-checker endpoint, which is the most expensive call in the system).
- **Frontend API base URL is hardcoded** in `dashboard.js`/`login.js`/`register.js` (`const BASE = 'https://...onrender.com'`) with the localhost alternative commented out. Should be environment-driven instead of requiring a manual code edit to switch between local/deployed backends.
- **JWT is stored in `localStorage`** on the frontend, which is readable by any JS running on the page — an XSS vulnerability anywhere in the dashboard would expose the token. An httpOnly cookie would be safer, but requires backend changes to how the token is issued/read.

---

## Frontend

✅ Built and functional — see [Frontend Pages](#frontend-pages) above. Served directly as static resources by this Spring Boot app (no separate frontend deployment/build step). Auth state lives in `localStorage` (`iet_token`); the dashboard talks to the API at a hardcoded `BASE` URL in `dashboard.js`/`login.js`/`register.js` (currently pointed at the deployed Render instance — swap to `localhost:4040` for local dev, see the commented-out line in `dashboard.js`).

---

## License

OWNER : (https://github.com/JustTheGreenPanther28)

---

## Links

- API Documentation: [Swagger UI](https://student-community-platform-ietscroll.onrender.com/swagger-ui/index.html#/)
- Website : [IET SCROLL](https://student-community-platform-ietscroll.onrender.com/)
