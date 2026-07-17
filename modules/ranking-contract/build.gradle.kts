plugins {
    `java-library`
}

// 랭킹 ZSET 키 규약만 담는다 — Redis 접속·명령은 modules:redis, 점수 정책은 각 앱의 몫이다.
dependencies {
}
