# IET Scroll — Backend API

A Spring Boot REST API powering the **IET Scroll** platform — a vibrant college community app for IET DAVV students. This backend supports features such as lost & found item management, team formation, AI-powered resume review, OTP-based authentication, and image moderation.

---

## Tech Stack

| Layer                  | Technology                           |
|------------------------|--------------------------------------|
| Framework              | Spring Boot                          |
| Security               | Spring Security (JWT / Basic Auth) |
| ORM                    | Spring Data JPA                      |
| Database               | Postgres                                |
| Image Storage          | Cloudinary                           |
| Image Moderation     | SightEngine API                     |
| AI / LLM              | Spring AI (Llama for resumes, Mistral for moderation) |
| Email                  | JavaMailSender (Gmail SMTP)          |
| PDF Parsing            | Apache Tika                          |
| API Documentation      | Swagger / OpenAPI 3                  |

---

## Features

- **User Registration & OTP Verification**  
  Register with institute email (@ietdavv.edu.in). An OTP is sent via Gmail and must be verified within 10 minutes.

- **Lost & Found**  
  Report lost/found items with image uploads. Images are moderated via SightEngine before storage in Cloudinary.

- **Team Finder**  
  Create, browse, and join teams. Team purposes are moderated by the Mistral LLM before saving.

- **Resume Checker**  
  Upload resumes (PDF/DOCX) to receive AI-generated feedback, including scores, missing keywords, and suggestions, via the Llama LLM.

- **Paginated Feeds**  
  View lost items, found items, and teams with pagination, sorted by latest.
  
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


---

## License

OWNER : (https://github.com/JustTheGreenPanther28)

---

## Links

- API Documentation: [Swagger UI](https://student-community-platform-ietscroll.onrender.com/swagger-ui/index.html#/)
- Website : [IET SCROLL](https://student-community-platform-ietscroll.onrender.com/)
