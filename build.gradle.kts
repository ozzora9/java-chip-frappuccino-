plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

javafx {
    version = "21.0.4"   // LTS + 안정
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    // ✨ 여기 실제 메인 클래스 이름으로 변경
    mainClass.set("com.example.studyplanner.HelloApplication")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}