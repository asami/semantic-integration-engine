FROM eclipse-temurin:17-jre

WORKDIR /app

# copy fat jar
COPY dist/semantic-integration-engine.jar /app/semantic-integration-engine.jar

# environment variables (override by compose)
ENV FUSEKI_URL=http://fuseki:3030/ds
ENV CHROMA_URL=http://chromadb:8000
ENV SIESERVER_PORT=9050

# expose port for clarity (optional)
EXPOSE 9050

# run the app normally
CMD ["java", "-jar", "/app/semantic-integration-engine.jar"]
