# 공식 openjdk 대신 유지보수가 잘 되는 eclipse-temurin 최신 이미지 사용
FROM eclipse-temurin:17-jdk-alpine

# 2. 컨테이너 내부 작업 디렉토리 지정
WORKDIR /app

# 3. 아까 ./gradlew clean bootJar로 빌드한 jar 파일을 컨테이너 내부로 복사
COPY build/libs/app.jar app.jar

# 4. 프로필 설정을 prod(Docker MySQL)로 명시하여 애플리케이션 가동
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]