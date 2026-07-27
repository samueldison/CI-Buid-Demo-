ARG BASE_IMAGE=bellsoft/liberica-openjdk-alpine:17

# Use bellsoft/liberica as base image
FROM ${BASE_IMAGE} AS builder

# Set working directory as an environmental variable
ENV WORK_DIR=/app

# Set the working directory
WORKDIR ${WORK_DIR}

# Set a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Switch to the non-root user
USER spring:spring

# Copy the application JAR
COPY target/*.jar app.jar

# Expose application port
EXPOSE 8085

# Set the entry point for the container
ENTRYPOINT ["java","-jar","app.jar"]