package com.studycafe.ranking.push;

/**
 * 푸시 알림 페이로드. 서비스워커가 {@code event.data.json()} 으로 읽어 알림을 띄운다.
 * url 은 알림 클릭 시 열/포커스할 앱 경로.
 */
public record PushMessage(String title, String body, String url) {
}
