# File/Media URL Storage Rules - iGoGo Backend

## Overview

This document defines how to store and serve file/media URLs to ensure:

- Database storage efficiency (relative paths only)
- Flexibility in changing CDN/storage providers
- Consistent absolute URLs in API responses
- Clean separation between storage and presentation layers

## Core Principles

### 1. Database Storage: Relative Paths Only

- **Always store relative paths** in entity columns.
- Never store full URLs with a domain.
- Paths start with `/`, relative to the storage root.

```java
// Good - relative paths persisted on the entity
user.setPhotoUrl("/uploads/homestays/abc123/photo.jpg");
user.setFrontIdUrl("/uploads/id-cards/front/xyz789.jpg");

// Bad - full URLs in the DB
user.setPhotoUrl("https://cdn.igogo.vn/uploads/homestays/abc123/photo.jpg"); // ❌
user.setPhotoUrl("http://localhost:2207/uploads/photo.jpg");                 // ❌
```

### 2. API Response: Absolute URLs Only

- **Always return absolute URLs** in responses.
- Convert relative → absolute when mapping entities to response DTOs.
- Use the configured base URL (CDN or API domain).

```json
// Good
{ "photoUrl": "https://cdn.igogo.vn/uploads/homestays/abc123/photo.jpg" }

// Bad
{ "photoUrl": "/uploads/homestays/abc123/photo.jpg" }   // ❌
```

### 3. When Saving: Store Relative Paths

```java
// Good - extract and persist the relative path
FileUploadResult result = mediaService.upload(file);
// result.url() might be https://cdn.igogo.vn/uploads/media/photo.jpg
String relativePath = urlConverter.toRelativePath(result.url()); // /uploads/media/photo.jpg
media.setFileUrl(relativePath);
mediaRepository.save(media);

// Bad - persist the absolute URL
media.setFileUrl(result.url());   // ❌
```

## Configuration

### Environment / application.yml

```yaml
app:
  file:
    base-url: ${FILE_BASE_URL:http://localhost:2207}   # CDN or API domain
    upload-dir: ${UPLOAD_DIR:./uploads}
```

```bash
# Environment variables
FILE_BASE_URL=https://cdn.igogo.vn        # production
FILE_BASE_URL=http://localhost:2207       # development
```

### Typed Properties

```java
@ConfigurationProperties(prefix = "app.file")
public record FileProperties(String baseUrl, String uploadDir) {}
```

## Helper Component

### UrlConverter

```java
@Component
@RequiredArgsConstructor
public class UrlConverter {

    private final FileProperties fileProperties;

    /** Convert a stored relative path into an absolute URL. */
    public String toAbsoluteUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;   // already absolute
        }
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return fileProperties.baseUrl() + path;
    }

    /** Convert an absolute URL into a relative path for storage. */
    public String toRelativePath(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isBlank()) {
            return null;
        }
        if (!absoluteUrl.startsWith("http://") && !absoluteUrl.startsWith("https://")) {
            return absoluteUrl.startsWith("/") ? absoluteUrl : "/" + absoluteUrl;
        }
        try {
            return URI.create(absoluteUrl).getPath();
        } catch (IllegalArgumentException ex) {
            return absoluteUrl;
        }
    }

    /** Convert a list of relative paths. */
    public List<String> toAbsoluteUrls(List<String> relativePaths) {
        return relativePaths.stream().map(this::toAbsoluteUrl).toList();
    }
}
```

## Implementation Patterns

### Pattern 1: MapStruct Mapper (Recommended)

Inject `UrlConverter` into a MapStruct mapper and convert during entity → DTO mapping:

```java
@Mapper(componentModel = "spring", uses = UrlConverter.class)
public interface RegistrationMapper {

    @Mapping(target = "photoUrl",   source = "photoUrl",   qualifiedByName = "toAbsoluteUrl")
    @Mapping(target = "frontIdUrl", source = "frontIdUrl", qualifiedByName = "toAbsoluteUrl")
    @Mapping(target = "backIdUrl",  source = "backIdUrl",  qualifiedByName = "toAbsoluteUrl")
    RegistrationResponse toResponse(Registration entity);
}

// Mark the converter method for MapStruct
@Component
public class UrlConverter {
    @Named("toAbsoluteUrl")
    public String toAbsoluteUrl(String relativePath) { /* as above */ }
}
```

### Pattern 2: Service-Layer Conversion

```java
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final UrlConverter urlConverter;

    @Transactional(readOnly = true)
    public RegistrationResponse findById(UUID id) {
        Registration reg = registrationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("registration.notFound"));

        return new RegistrationResponse(
            reg.getId(),
            urlConverter.toAbsoluteUrl(reg.getPhotoUrl()),
            urlConverter.toAbsoluteUrl(reg.getFrontIdUrl()),
            urlConverter.toAbsoluteUrl(reg.getBackIdUrl()));
    }
}
```

### Pattern 3: Saving Files

```java
@Transactional
public MediaResponse uploadMedia(MultipartFile file) {
    FileUploadResult result = mediaService.upload(file);          // may return absolute URL
    String relativePath = urlConverter.toRelativePath(result.url());

    Media media = new Media();
    media.setFileUrl(relativePath);                                // store relative
    media.setFileName(file.getOriginalFilename());
    media.setFileSize(file.getSize());
    media = mediaRepository.save(media);

    return new MediaResponse(
        urlConverter.toAbsoluteUrl(media.getFileUrl()),            // return absolute
        media.getFileName(),
        media.getFileSize());
}
```

## Common URL Fields

### Entity Columns (snake_case in DB, relative)

```
photo_url, front_id_url, back_id_url, ambassador_photo_url, delegate_photo_url,
thumbnail_url, video_url, media_url, file_url, attachment_url
```

```java
@Column(name = "photo_url")
private String photoUrl;          // stored relative, e.g. /uploads/photo.jpg
```

### Response DTO Fields (camelCase, absolute)

```java
public record MediaResponse(String fileUrl, String thumbnailUrl, String fileName, long fileSize) {}
// fileUrl / thumbnailUrl are absolute URLs
```

## Lists

```java
@Transactional(readOnly = true)
public List<MediaResponse> getMediaList(UUID registrationId) {
    return mediaRepository.findByRegistrationId(registrationId).stream()
        .map(m -> new MediaResponse(
            urlConverter.toAbsoluteUrl(m.getFileUrl()),
            urlConverter.toAbsoluteUrl(m.getThumbnailUrl()),
            m.getFileName(),
            m.getFileSize()))
        .toList();
}
```

## Null Handling

`UrlConverter` returns `null` for null/blank input:

```java
urlConverter.toAbsoluteUrl(null)   // => null
urlConverter.toAbsoluteUrl("")     // => null
urlConverter.toAbsoluteUrl("/uploads/photo.jpg")
    // => https://cdn.igogo.vn/uploads/photo.jpg
```

## Best Practices

### 1. Always Use UrlConverter

```java
// Good
response.photoUrl = urlConverter.toAbsoluteUrl(entity.getPhotoUrl());
// Bad - manual concatenation
response.photoUrl = baseUrl + entity.getPhotoUrl();   // ❌
```

### 2. Convert in the Mapper/Service, Not the Controller

Never return an entity (with relative paths) from a controller — map to a DTO with absolute URLs first (see 03-dto-response.md).

### 3. Document URL Format with @Schema

```java
@Schema(description = "Absolute URL to the uploaded photo",
        example = "https://cdn.igogo.vn/uploads/photos/abc123.jpg")
String photoUrl
```

### 4. Test URL Conversions

```java
class UrlConverterTest {

    private final UrlConverter converter =
        new UrlConverter(new FileProperties("https://cdn.igogo.vn", "./uploads"));

    @Test
    void convertsRelativeToAbsolute() {
        assertThat(converter.toAbsoluteUrl("/uploads/photo.jpg"))
            .isEqualTo("https://cdn.igogo.vn/uploads/photo.jpg");
    }

    @Test
    void handlesNull() {
        assertThat(converter.toAbsoluteUrl(null)).isNull();
    }

    @Test
    void extractsRelativePath() {
        assertThat(converter.toRelativePath("https://cdn.igogo.vn/uploads/photo.jpg"))
            .isEqualTo("/uploads/photo.jpg");
    }
}
```

## Migration Strategy

If existing rows contain absolute URLs, strip the host in a Flyway migration:

```sql
-- V..._normalize_file_urls.sql
UPDATE dtb_homestay_registrations
SET photo_url = REGEXP_REPLACE(photo_url, '^https?://[^/]+', '')
WHERE photo_url IS NOT NULL AND photo_url LIKE 'http%';

UPDATE dtb_media_files
SET file_url = REGEXP_REPLACE(file_url, '^https?://[^/]+', '')
WHERE file_url IS NOT NULL AND file_url LIKE 'http%';
```

## Quick Checklist

- [ ] DB stores relative paths (e.g. `/uploads/photo.jpg`)
- [ ] API responses return absolute URLs
- [ ] `UrlConverter` used for all conversions
- [ ] Upload flow extracts relative paths before saving
- [ ] Response DTOs document the absolute URL with `@Schema`
- [ ] Null/blank values handled gracefully
- [ ] `app.file.base-url` (`FILE_BASE_URL`) configured
- [ ] URL fields: camelCase in DTOs, snake_case columns in DB

## Summary

| Layer | Format | Example |
|-------|--------|---------|
| Database column | Relative path | `/uploads/photo.jpg` |
| Service/Mapper (read) | Convert to absolute | `https://cdn.igogo.vn/uploads/photo.jpg` |
| Service (write) | Convert to relative | `/uploads/photo.jpg` |
| Response DTO | Absolute URL | `https://cdn.igogo.vn/uploads/photo.jpg` |
| API Response | Absolute URL | `https://cdn.igogo.vn/uploads/photo.jpg` |
