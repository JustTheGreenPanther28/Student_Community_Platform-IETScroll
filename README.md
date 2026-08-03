# IET Scroll — Full-Stack Campus Community Platform

A Spring Boot REST API, paired with a working vanilla HTML/CSS/JS frontend, powering
**IET Scroll** — a community app for IET DAVV students. Covers lost & found item
management, team formation with skill-based matching, AI-powered resume review,
OTP-based authentication, and image moderation — restricted to `@ietdavv.edu.in`
emails.

> **Status:** Both backend and frontend are functional and deployed. Not a
> backend-only project — `src/main/resources/static/` ships a complete dashboard,
> login, and registration flow that consumes every endpoint below.

---

## Tech Stack

| Layer                | Technology                                            |
|-----------------------|--------------------------------------------------------|
| Framework             | Spring Boot                                            |
| Security              | Spring Security (stateless JWT), role-based access     |
| ORM                   | Spring Data JPA                                        |
| Database              | PostgreSQL (Supabase)                                  |
| Image Storage         | Cloudinary                                              |
| Image Moderation      | SightEngine API (nudity, gore, violence, weapons, self-harm, offensive content) |
| AI / LLM              | Spring AI — Llama (resume scoring), Mistral (team-purpose moderation) |
| Email                 | JavaMailSender (Gmail SMTP)                             |
| PDF/DOCX Parsing      | Apache Tika                                             |
| API Documentation     | Swagger / OpenAPI 3                                     |
| Frontend              | HTML, CSS, vanilla JS (dashboard, login, register, safety pages) |
| Uptime                | Scheduled self-ping (`@Scheduled`) to avoid Render free-tier sleep |

---

## Features

- **User Registration & OTP Verification**
  Register with institute email (`@ietdavv.edu.in`). A 6-digit OTP (via
  `SecureRandom`) is emailed through Gmail SMTP and must be verified within 10
  minutes before login is allowed.

- **JWT Auth with Role-Based Access**
  Stateless JWT issued on login, carrying the user's role as a claim. An
  `/api/v1/admin/**` route pattern is reserved and gated behind `ROLE_ADMIN` in
  the security config (no admin endpoints are implemented yet — the gate exists,
  the routes don't).

- **Lost & Found**
  Report lost/found items with an image. Images are validated by content type,
  then run through SightEngine moderation before being uploaded to Cloudinary.
  Per-user caps are enforced server-side: max **2** active lost-item requests,
  max **3** active found-item requests.

- **Team Finder with Skill Matching**
  Create a team with a purpose (validated by the Mistral LLM before saving),
  a max size (3–20 members), a privacy setting (public/private), and an optional
  list of required skills. One active team per user.

- **Team Join-Request Workflow**
  Users apply to a team with a message. The team owner can view pending
  requests, accept (capacity-checked against max size), reject, or remove an
  existing member. Applicants can view the status of everything they've applied
  to.

- **Resume Checker**
  Upload a resume (PDF/DOCX). Apache Tika extracts the text, which is sent to
  the Llama chat client via Spring AI and returned as a structured
  `QualityOfResume` object (score, missing keywords, suggestions) — not raw
  LLM text.

- **Paginated Feeds**
  Lost items, found items, and teams are all paginated via a shared
  `PagedResponseDTO` wrapper, sorted by most recent.

- **Safety & Trust Page**
  A dedicated frontend page covering anti-ragging policy, equality, privacy,
  platform rules, and a liability disclaimer — served as static HTML.

- **Uptime Workaround**
  A scheduled job self-pings `/actuator/health` every hour to reduce the
  chance of the app being asleep when a real request comes in, working around
  Render's free-tier spin-down.

---

## API Endpoints

### User
`/api/v1/user`

| Method | Endpoint                  | Description                                  |
|--------|----------------------------|-----------------------------------------------|
| POST   | `/register`                | Register with institute email; triggers OTP  |
| GET    | `/`                         | Get current user's profile                    |
| PATCH  | `/username/{newUsername}`  | Update username                                |
| PATCH  | `/fullname/{fullname}`     | Update full name                               |

### OTP
`/api/v1/otp`

| Method | Endpoint  | Description                     |
|--------|-----------|-----------------------------------|
| POST   | `/verify` | Verify OTP and activate account |

### Lost Items
`/api/v1/lost-item`

| Method | Endpoint | Description                                     |
|--------|----------|--------------------------------------------------|
| POST   | `/`      | Report a lost item (image + details, max 2 active) |
| GET    | `/me`    | Get current user's active lost items             |
| GET    | `/`      | Get all open lost items (paginated)               |
| PATCH  | `/close` | Close a lost item request                         |

### Found Items
`/api/v1/found-item`

| Method | Endpoint | Description                                        |
|--------|----------|-------------------------------------------------------|
| POST   | `/`      | Report a found item (image + details, max 3 active)  |
| GET    | `/me`    | Get current user's active found items                |
| GET    | `/`      | Get all pending found items (paginated)               |
| PATCH  | `/close` | Close a found item request                             |

### Teams
`/api/v1/team`

| Method | Endpoint     | Description                                          |
|--------|--------------|---------------------------------------------------------|
| POST   | `/`          | Create a team (AI-moderated purpose, optional skills)  |
| GET    | `/`          | Browse all active public teams (paginated)              |
| GET    | `/me`        | Get authenticated user's active team                    |
| PATCH  | `/close`     | Close your team                                          |
| PATCH  | `/team-size` | Update max team size (min: 3, max: 20)                  |

### Team Join Requests
`/api/v1/request-team`

| Method | Endpoint                    | Description                              |
|--------|-------------------------------|---------------------------------------------|
| POST   | `/`                            | Submit a join request with a message      |
| GET    | `/requests`                    | View pending join requests (team owner)    |
| GET    | `/team-members`                | View accepted team members                  |
| PATCH  | `/accept/{applicantEmail}`     | Accept a join request (capacity-checked)   |
| PATCH  | `/reject/{applicantEmail}`     | Reject a join request                        |
| PATCH  | `/remove/{applicantEmail}`     | Remove a team member                          |
| GET    | `/my-application`              | View all your submitted applications         |

### Resume Checker
`/api/v1/ietscroll/resume`

| Method | Endpoint   | Description                                              |
|--------|------------|-------------------------------------------------------------|
| POST   | `/quality` | Upload resume (PDF/DOCX) for AI-generated quality report |

---

## Key Business Rules

- Only `@ietdavv.edu.in` email addresses can register (plus one configurable
  admin email).
- OTP expires after 10 minutes; account must be verified before login.
- Users can have at most 2 active lost-item requests and 3 active found-item
  requests at a time.
- Users can create only 1 active team at a time; team size is capped 3–20.
- Accepting a join request is capacity-checked — it's rejected if the team is
  already at `maxMember`.
- All uploaded images pass through SightEngine moderation (nudity, gore,
  violence, self-harm, weapons, offensive content, alcohol/drugs) with
  per-category confidence thresholds.
- Team purpose text is validated by the Mistral LLM before saving.
- Resume analysis uses Llama via Spring AI, with Apache Tika handling
  PDF/DOCX text extraction.

---

## Frontend

Not a stub — `src/main/resources/static/` contains a working client:

- `index.html` / `login.html` / `register.html` — landing, login, and
  registration, each with matching CSS/JS
- `dashboard.html` + `dashboard.js` (~780 lines) — tabs for Lost & Found
  browsing/submission with image preview, Team browsing/creation, the full
  join-request workflow (apply, view requests, accept/reject/remove), resume
  upload, profile editing, and pagination controls, all wired directly to the
  REST API above
- `safety.html` — Safety & Trust page (anti-ragging, equality, privacy, rules,
  disclaimer)

API docs are available via Swagger UI once the backend is running.

---

## Known Rough Edges

Being upfront about what's unfinished, for anyone reading the code:

- `TeamService.getMyTeamPosts()` is unimplemented (returns `null`).
- `CloudinaryService.deleteImage()` exists but isn't called anywhere yet — no
  cleanup path for removed images.
- `/api/v1/otp/resend` is reserved as a security constant but has no
  controller endpoint behind it yet.
- `/api/v1/admin/**` is role-gated but has no implemented endpoints yet.
- A couple of leftover debug `System.out.println` calls in
  `TeamJoinRequestServiceImpl`.

---

## License

Owner: [JustTheGreenPanther28](https://github.com/JustTheGreenPanther28)

---

## Links

- API Documentation: [Swagger UI](https://student-community-platform-ietscroll.onrender.com/swagger-ui/index.html#/)
- Website: [IET Scroll](https://student-community-platform-ietscroll.onrender.com/)
