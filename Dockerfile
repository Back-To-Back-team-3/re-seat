# 1. 유지보수가 잘 되는 eclipse-temurin 이미지 사용
FROM eclipse-temurin:17-jdk

# 컨테이너 내부 작업 디렉토리 지정
WORKDIR /app

# 2. 보안을 위한 non-root 사용자 및 그룹 생성
RUN addgroup -S spring && adduser -S spring -G spring

# 3. jar 복사 시 소유권을 spring 사용자로 명시하여 보안 강화
COPY --chown=spring:spring build/libs/app.jar app.jar

# 4. root가 아닌 spring 사용자로 전환
USER spring

# 5. 프로필 설정을 prod로 명시하여 애플리케이션 가동
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
