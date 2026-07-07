# ----- Build stage: compile + package the Spring Boot jar (Java 21 / Maven) -----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster rebuilds.
COPY pom.xml .
RUN mvn -q -e -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ----- Runtime stage: JRE + Python mesh toolchain (printability check / auto-repair) -----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Python + mesh libraries used by scripts/mesh_tools.py:
#  - trimesh analysis, pymeshfix repair, Pillow (decode/re-embed GLB textures when baking a base)
#  - matplotlib + shapely: turn the signature text into glyph outlines/polygons
#  - manifold3d: robust boolean engine to engrave/raise the signature on the base
#  - mapbox_earcut: polygon triangulation used by trimesh.creation.extrude_polygon
RUN apt-get update \
     && apt-get install -y --no-install-recommends python3 python3-pip \
     && pip3 install --no-cache-dir --break-system-packages \
     numpy trimesh pymeshfix Pillow matplotlib shapely manifold3d mapbox_earcut \
     && apt-get clean && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
