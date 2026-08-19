# org.json 은 플랫폼 제공이라 건드릴 것이 없다.
# Compose 와 코루틴은 각자 consumer 규칙을 들고 온다.
-dontwarn org.json.**

# 폴더/파일 선택기를 여는 계약 클래스들. R8 이 이름을 지우면 launcher 가 조용히 아무 일도
# 하지 않는다 — release 빌드에서만 선택기가 안 열리는 증상으로 나타난다.
-keep class androidx.activity.result.contract.** { *; }
-keep class * extends androidx.activity.result.contract.ActivityResultContract { *; }
