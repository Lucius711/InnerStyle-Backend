# InnerStyle Backend — 2D/Text → Animated 3D (MeshyAI)

Spring Boot module that turns a 2D image (or a text prompt) into a textured, **colored**,
optionally **posed**, **optimized**, **rigged** and **animated** 3D model using the
[MeshyAI](https://docs.meshy.ai) API. Built to the conventions in `claude.md` / `rules/`
(Java 21 · Maven · Spring Data JPA · Flyway · Bean Validation · springdoc).

## What it does

| Capability | Endpoint | MeshyAI |
|-----------|----------|---------|
| 2D image → 3D (geometry + color + pose) | `POST /common/3d/image-to-3d` | `image-to-3d` |
| Text → 3D mesh (preview) | `POST /common/3d/text-to-3d` | `text-to-3d` (preview) |
| Add color/texture to a preview | `POST /common/3d/refine` | `text-to-3d` (refine) |
| Optimize (topology / polycount) | `POST /common/3d/remesh` | `remesh` |
| Re-color / re-texture | `POST /common/3d/retexture` | `retexture` |
| Rig (skeleton + walk/run) | `POST /common/3d/rig` | `rigging` |
| Animate a rigged character | `POST /common/3d/animate` | `animations` |
| Get a task + results | `GET /common/3d/tasks/{id}` | — |
| List tasks | `GET /common/3d/tasks` | — |
| Meshy callback (internal) | `POST /webhooks/meshy` | webhook |

- **Color** → `enablePbr` + `texturePrompt`/`textureImageUrl` produce base-color + PBR maps
  (metallic / roughness / normal / emission), returned under `textureUrls`.
- **Pose** → `poseMode: a-pose | t-pose` on image/text-to-3D.
- **Animation** → `rig` then `animate`; outputs (walking/running/custom action GLB+FBX) come
  back under `animationUrls`.
- **Optimization** → `remesh` (quad/triangle, target polycount, formats).

## Async model

MeshyAI is asynchronous: every "create" call returns immediately with a `PENDING` task.
Completion is delivered two ways (per request: *Cả hai*):

1. **Webhook (primary)** — Meshy calls `POST /webhooks/meshy`; the payload is merged into the
   stored task. Protect it with the shared `MESHY_WEBHOOK_SECRET` (`X-Webhook-Secret` header).
2. **Polling (fallback)** — `MeshyTaskPoller` reconciles non-terminal tasks every 15s.

Poll `GET /common/3d/tasks/{id}` until `status = SUCCEEDED` (or `FAILED`).

## Configuration

Secrets live in `.env` (git-ignored). Copy `.env.example` → `.env` and fill in the key you
provide later:

```dotenv
MESHY_API_KEY=msy_xxx          # <-- paste your MeshyAI key here
MESHY_BASE_URL=https://api.meshy.ai
MESHY_WEBHOOK_SECRET=some_random_secret
DATABASE_URL=jdbc:postgresql://localhost:5432/innerstyle
DATABASE_USERNAME=innerstyle
DATABASE_PASSWORD=innerstyle
```

`spring-dotenv` loads `.env` automatically; values are bound to `app.meshy.*`
(`MeshyProperties`). The app starts without a key, but generation calls return a clear
`502 meshy.apiKeyMissing` until one is set.

## Run

```bash
# Requires JDK 21 + a PostgreSQL database
./mvnw spring-boot:run
# Swagger UI: http://localhost:2207/swagger-ui.html
```

Flyway creates `dtb_meshy_tasks` on startup (`src/main/resources/db/migration`).

## Example flow (image → animated, colored, posed model)

```bash
# 1) Image -> 3D with color (PBR) + A-pose + optimization
curl -X POST localhost:2207/common/3d/image-to-3d -H 'Content-Type: application/json' -d '{
  "imageUrl": "https://example.com/character.png",
  "enablePbr": true, "shouldRemesh": true, "targetPolycount": 100000, "poseMode": "a-pose"
}'
# -> { "data": { "id": "<taskId>", "status": "PENDING" } }

# 2) When SUCCEEDED, rig it
curl -X POST localhost:2207/common/3d/rig -H 'Content-Type: application/json' \
  -d '{ "sourceTaskId": "<taskId>", "heightMeters": 1.7 }'

# 3) Animate the rig (action 92 from Meshy's animation library)
curl -X POST localhost:2207/common/3d/animate -H 'Content-Type: application/json' \
  -d '{ "rigTaskId": "<rigTaskId>", "actionId": 92 }'
```

## Layout

```
com.innerstyle
├── config/OpenApiConfig
├── common/{response, exception}        # ApiResponse + global error handling
└── meshy/
    ├── client/                         # MeshyClient (RestClient) + API DTOs
    ├── config/                         # MeshyProperties, RestClient bean
    ├── controller/                     # REST + webhook
    ├── dto/{request,response}
    ├── entity/ (+ enums) / repository / mapper
    └── service/ (interface + impl + poller)
```

> Note: the result URLs returned by Meshy are external, signed and time-limited; they are
> stored verbatim and refreshed on each task sync.
